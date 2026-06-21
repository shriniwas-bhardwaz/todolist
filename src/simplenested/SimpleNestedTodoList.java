package simplenested;

import java.util.*;


public final class SimpleNestedTodoList {

    public static final class Task {
        private final long id;
        private String text;
        private boolean completed;
        private Long parentId;   // null = root
        private double orderKey; // position among siblings; compared, never assumed contiguous

        private Task(long id, String text, Long parentId, double orderKey) {
            this.id = id;
            this.text = text;
            this.parentId = parentId;
            this.orderKey = orderKey;
        }

        public long getId() { return id; }
        public String getText() { return text; }
        public boolean isCompleted() { return completed; }
        public Long getParentId() { return parentId; }

        @Override
        public String toString() {
            return (completed ? "[x] " : "[ ] ") + text;
        }
    }


    private final Map<Long, Task> tasks = new LinkedHashMap<>();
    private long nextId = 1;

    // ----------------------------------------------------------------- create

    /** Adds a task as the last child of parentId (null = last root task). */
    public long add(String text, Long parentId) {
        if (parentId != null) requireTask(parentId); // validate parent exists
        long id = nextId++;
        // DB: nextOrderKey scans every task here (O(n)). In a database it is one
        //     indexed query — SELECT MAX(order_key) FROM tasks WHERE parent_id = ?
        //     with an index on (parent_id, order_key), so it is O(log n).
        double key = nextOrderKey(parentId);
        // DB: this becomes INSERT INTO tasks(id, text, parent_id, order_key) VALUES(...).
        tasks.put(id, new Task(id, text.trim(), parentId, key));
        return id;
    }

    public long addRootTask(String text)            { return add(text, null); }
    public long addChildTask(long parentId, String t) { return add(t, parentId); }

    // ----------------------------------------------------------------- mutate

    public void setCompleted(long taskId, boolean completed) {
        requireTask(taskId).completed = completed;
    }

    public void updateTask(long taskId, String newText) {
        requireTask(taskId).text = newText.trim();
    }

    /**
     * Moves a task under a new parent (null = root), appended as the last child.
     * The entire "move" is one parentId assignment plus a fresh orderKey —
     * descendants come along for free because they still point at this task.
     */
    public void move(long taskId, Long newParentId) {
        Task task = requireTask(taskId);
        if (newParentId != null) requireTask(newParentId);
        if (createsCycle(taskId, newParentId)) {
            throw new IllegalArgumentException(
                    "A task cannot be moved under itself or one of its descendants");
        }
        // DB: the entire move is a SINGLE-ROW write —
        //     UPDATE tasks SET parent_id = ?, order_key = ? WHERE id = ?
        //     The subtree comes along for free (descendants still reference this
        //     row's id), so no descendant rows are touched. This single-row,
        //     low-contention move is the main reason adjacency lists scale.
        task.parentId = newParentId;
        task.orderKey = nextOrderKey(newParentId); // DB: indexed MAX(order_key), see add()
    }

    /** Reorders a task to sit immediately before `beforeTaskId` among its siblings. */
    public void moveBefore(long taskId, long beforeTaskId) {
        Task task = requireTask(taskId);
        Task before = requireTask(beforeTaskId);
        if (createsCycle(taskId, before.parentId)) {
            throw new IllegalArgumentException(
                    "A task cannot be moved under itself or one of its descendants");
        }
        // DB: also a single-row UPDATE of (parent_id, order_key). The fractional
        //     order_key is what keeps reorder a ONE-row write — we slot between two
        //     neighbours instead of shifting every following sibling's position.
        //     Production systems use a string fractional index (e.g. LexoRank) here
        //     because doubles lose precision after many reorders between the same pair.
        task.parentId = before.parentId;

        // DB: childrenOf is an indexed range scan —
        //     SELECT * FROM tasks WHERE parent_id = ? ORDER BY order_key
        //     O(log n + c) with an index on (parent_id, order_key); only the
        //     neighbour's order_key is actually needed to compute the midpoint.
        List<Task> siblings = childrenOf(before.parentId); // ordered
        int idx = indexOfById(siblings, beforeTaskId);
        double lower = idx == 0 ? before.orderKey - 1.0 : siblings.get(idx - 1).orderKey;
        task.orderKey = (lower + before.orderKey) / 2.0;   // fractional index between neighbours
    }

    /**
     * Indent: the previous sibling becomes the new parent.
     * Returns false if there is no previous sibling.
     */
    public boolean indent(long taskId) {
        Task task = requireTask(taskId);
        List<Task> siblings = childrenOf(task.parentId);
        int idx = indexOfById(siblings, taskId);
        if (idx <= 0) return false;
        move(taskId, siblings.get(idx - 1).id);
        return true;
    }

    /**
     * Outdent: the task becomes the next sibling of its current parent.
     * Returns false if the task is already at root level.
     */
    public boolean outdent(long taskId) {
        Task task = requireTask(taskId);
        if (task.parentId == null) return false;
        Task parent = tasks.get(task.parentId);
        move(taskId, parent.parentId); // grandparent (or root if parent was root-level)
        return true;
    }

    /** Deletes a task and its whole subtree. */
    public void deleteTask(long taskId) {
        // DB: this hand-written downward walk (one childrenOf scan per node) is
        //     replaced by a single recursive query, e.g.
        //       WITH RECURSIVE subtree AS (
        //         SELECT id FROM tasks WHERE id = ?
        //         UNION ALL
        //         SELECT t.id FROM tasks t JOIN subtree s ON t.parent_id = s.id)
        //       DELETE FROM tasks WHERE id IN (SELECT id FROM subtree);
        //     or simpler: a self-referencing FK with ON DELETE CASCADE makes the
        //     single DELETE of the root row cascade to the whole subtree automatically.
        requireTask(taskId);
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(taskId);
        while (!stack.isEmpty()) {
            long id = stack.pop();
            for (Task child : childrenOf(id)) stack.push(child.id);
            tasks.remove(id);
        }
    }

    // ------------------------------------------------------------------ reads

    public Task getTask(long taskId) { return tasks.get(taskId); }

    /** Children of parentId (null = roots), sorted by orderKey. */
    public List<Task> childrenOf(Long parentId) {
        // DB: this O(n)-scan-then-sort is the single biggest in-memory weakness,
        //     and the one a database eliminates outright. With an index on
        //     (parent_id, order_key) it is an indexed range scan returning rows
        //     already in order — O(log n + c), no full-table scan, no sort:
        //       SELECT * FROM tasks WHERE parent_id = ? ORDER BY order_key
        //     (parent_id IS NULL for the roots).
        List<Task> result = new ArrayList<>();
        for (Task t : tasks.values()) {
            if (Objects.equals(t.parentId, parentId)) result.add(t);
        }
        result.sort(Comparator.comparingDouble(t -> t.orderKey));
        return result;
    }

    public String display() {
        // DB: rendering the whole tree here is O(n^2 log n) because appendChildren
        //     calls childrenOf (a full scan) once per node. Two ways a database
        //     fixes this:
        //       1. One indexed query per expanded level (WHERE parent_id = ?) —
        //          UIs only load what is visible, so the full tree is rarely built.
        //       2. For a full materialise, a single recursive CTE walks the tree in
        //          one pass: WITH RECURSIVE subtree AS (... UNION ALL ...).
        StringBuilder out = new StringBuilder();
        appendChildren(null, 0, out);
        return out.toString();
    }

    private void appendChildren(Long parentId, int depth, StringBuilder out) {
        for (Task task : childrenOf(parentId)) {
            out.append("    ".repeat(depth))
               .append(task)
               .append(" (id=").append(task.id).append(")")
               .append(System.lineSeparator());
            appendChildren(task.id, depth + 1, out); // recurse on this task's children
        }
    }

    // ---------------------------------------------------------------- helpers

    private Task requireTask(long taskId) {
        Task task = tasks.get(taskId);
        if (task == null) throw new IllegalArgumentException("Task does not exist: " + taskId);
        return task;
    }

    /**
     * Walks the parent chain upward; a cycle exists if we meet taskId again.
     *
     * DB: each step is a primary-key lookup (SELECT parent_id FROM tasks WHERE id = ?),
     *     so the climb is O(depth) point-reads — or a single upward recursive CTE.
     *     This O(depth) cycle check is the one cost adjacency lists cannot avoid; it
     *     is the price paid for making moves single-row writes.
     */
    private boolean createsCycle(long taskId, Long proposedParentId) {
        Long current = proposedParentId;
        while (current != null) {
            if (current == taskId) return true;
            current = tasks.get(current).parentId;
        }
        return false;
    }

    /**
     * One past the largest sibling orderKey, so new tasks append at the end.
     *
     * DB: replaced by SELECT MAX(order_key) FROM tasks WHERE parent_id = ?, which is
     *     O(log n) with an index on (parent_id, order_key) instead of this O(n) scan.
     */
    private double nextOrderKey(Long parentId) {
        double max = 0.0;
        boolean any = false;
        for (Task t : tasks.values()) {
            if (Objects.equals(t.parentId, parentId)) {
                max = any ? Math.max(max, t.orderKey) : t.orderKey;
                any = true;
            }
        }
        return any ? max + 1.0 : 0.0;
    }

    private static int indexOfById(List<Task> list, long id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == id) return i;
        }
        return -1;
    }
}
