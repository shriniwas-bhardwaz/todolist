package bestsnested;

import java.util.*;


public final class SimpleNestedTodoList {

    public static final class Task {
        private final long id;
        private String text;
        private boolean completed;
        private Long parentId;   // null = root
        private double orderKey; // position among siblings; compared, never assumed contiguous

        /**
         * Constructs a single tree node (one row in the adjacency list).
         * - Stores immutable id plus mutable text/parentId/orderKey.
         * - parentId is the UP-link (null = root); orderKey positions it among siblings.
         * - Private: nodes are only created via the enclosing list's add().
         * - O(1).
         */
        private Task(long id, String text, Long parentId, double orderKey) {
            this.id = id;
            this.text = text;
            this.parentId = parentId;
            this.orderKey = orderKey;
        }

        /**
         * Returns this node's stable unique id.
         * - Used as the key into the tasks map and as the parentId of children.
         * - O(1).
         */
        public long getId() { return id; }
        /**
         * Returns the task's display text.
         * - O(1).
         */
        public String getText() { return text; }
        /**
         * Reports whether this task is marked done.
         * - Completion is per-node only; it does not cascade to children.
         * - O(1).
         */
        public boolean isCompleted() { return completed; }
        /**
         * Returns the id of this node's parent, or null if it is a root.
         * - The authoritative UP-link used for cycle checks and outdent.
         * - O(1).
         */
        public Long getParentId() { return parentId; }

        /**
         * Renders the node as a checkbox line for display.
         * - "[x] " when completed, "[ ] " otherwise, followed by the text.
         * - O(1).
         */
        @Override
        public String toString() {
            return (completed ? "[x] " : "[ ] ") + text;
        }
    }


    private final Map<Long, Task> tasks = new LinkedHashMap<>();
    private long nextId = 1;

    // ----------------------------------------------------------------- create

    /**
     * Adds a task as the last child of parentId (null = last root task).
     * - Validates the parent exists (root parent is allowed via null).
     * - Allocates the next sequential id and an orderKey past the largest sibling.
     * - Inserts the new node into the flat tasks map (the adjacency list).
     * - Returns the new task's id.
     * - O(n) here because nextOrderKey scans every task; a DB index makes it O(log n).
     */
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

    /**
     * Convenience wrapper to add a top-level (root) task.
     * - Delegates to add() with a null parent.
     * - O(n) (inherits add()'s cost).
     */
    public long addRootTask(String text)            { return add(text, null); }
    /**
     * Convenience wrapper to add a child under an existing task.
     * - Delegates to add() with the given parentId.
     * - O(n) (inherits add()'s cost).
     */
    public long addChildTask(long parentId, String t) { return add(t, parentId); }

    // ----------------------------------------------------------------- mutate

    /**
     * Marks a single task complete or incomplete.
     * - Looks the node up by id and flips its completed flag in place.
     * - Affects only that node, not its subtree.
     * - O(1).
     */
    public void setCompleted(long taskId, boolean completed) {
        requireTask(taskId).completed = completed;
    }

    /**
     * Edits a task's display text.
     * - Looks the node up by id and replaces its trimmed text in place.
     * - Tree structure (parent/order) is unchanged.
     * - O(1).
     */
    public void updateTask(long taskId, String newText) {
        requireTask(taskId).text = newText.trim();
    }

    /**
     * Moves a task under a new parent (null = root), appended as the last child.
     * - Validates both the moved task and the target parent exist.
     * - Rejects moves that would create a cycle (moving under self/descendant).
     * - Re-parents by reassigning parentId and a fresh end-of-list orderKey.
     * - The whole subtree follows for free: descendants still reference this id,
     *   so no descendant nodes are touched (single-row update in a DB).
     * - O(depth) for the cycle check + O(n) for the orderKey scan.
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

    /**
     * Reorders a task to sit immediately before `beforeTaskId` among its siblings.
     * - Adopts the reference task's parent, then rejects the move if it forms a cycle.
     * - Computes a fractional orderKey halfway between the reference and its predecessor,
     *   so only one node's position changes (no shifting of following siblings).
     * - O(depth) cycle check + O(n) to fetch/sort the sibling list.
     */
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
     * - Finds the task's position among its ordered siblings.
     * - Returns false if it is the first sibling (no previous to indent under).
     * - Otherwise moves it (and its subtree) under the preceding sibling.
     * - O(n) (dominated by the sibling lookup and move).
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
     * - Returns false if the task is already at root level (no parent to climb past).
     * - Re-parents to the grandparent (or root if the parent was root-level) and slots
     *   the task IMMEDIATELY AFTER its former parent, not at the end of the list.
     * - Uses a fractional orderKey between the parent and the parent's next sibling,
     *   so only this one node's position changes (subtree follows for free).
     * - No cycle check needed: the grandparent is always an ancestor, never a descendant.
     * - O(depth) to reach the parent + O(n) to fetch/sort the grandparent's children.
     */
    public boolean outdent(long taskId) {
        Task task = requireTask(taskId);
        if (task.parentId == null) return false;
        Task parent = tasks.get(task.parentId);
        Long grandParentId = parent.parentId;

        // Read the grandparent's children BEFORE re-parenting, so `task` (still under
        // `parent`) is not yet in this list and `parent`'s real neighbours are visible.
        // DB: SELECT * FROM tasks WHERE parent_id = ? ORDER BY order_key — only the
        //     parent's order_key and its next sibling's order_key are actually needed.
        List<Task> siblings = childrenOf(grandParentId);
        int parentIdx = indexOfById(siblings, parent.id);
        double upper = (parentIdx + 1 < siblings.size())
                ? siblings.get(parentIdx + 1).orderKey   // the sibling right after the parent
                : parent.orderKey + 1.0;                 // parent is last → leave room past it

        // DB: single-row UPDATE of (parent_id, order_key); the midpoint keeps it a
        //     one-row write, matching OptimizedNestedTodoList's "insert after parent".
        task.parentId = grandParentId;
        task.orderKey = (parent.orderKey + upper) / 2.0; // slot right after the former parent
        return true;
    }

    /**
     * Deletes a task and its whole subtree.
     * - Validates the root of the subtree exists.
     * - Walks downward with an explicit stack, removing each visited node from the map.
     * - Children are discovered via childrenOf at every step (no orphan rows left behind).
     * - O(n^2) here (a childrenOf scan per node); a DB does it in one cascade/recursive query.
     */
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

    /**
     * Fetches a single task by id.
     * - Direct map lookup; returns null if the id is unknown (no exception).
     * - O(1).
     */
    public Task getTask(long taskId) { return tasks.get(taskId); }

    /**
     * Children of parentId (null = roots), sorted by orderKey.
     * - Scans every task to collect those whose parentId matches.
     * - Sorts the matches by their fractional orderKey to produce sibling order.
     * - O(n + c log c): full scan plus a sort of the c matches (a DB does an indexed range scan).
     */
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

    /**
     * Renders the entire tree as an indented, human-readable string.
     * - Starts from the roots and recursively appends each subtree.
     * - Indentation depth reflects nesting level.
     * - O(n^2 log n) because appendChildren calls childrenOf (a full scan) per node.
     */
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

    /**
     * Recursive helper that writes one level of the tree and descends into each child.
     * - Prepends depth-proportional indentation, then the task and its id.
     * - Calls itself on each child so the whole subtree is rendered depth-first.
     * - O(n^2 log n) across the full tree (childrenOf scan per node).
     */
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

    /**
     * Looks up a task by id, failing fast if it does not exist.
     * - Returns the node, or throws IllegalArgumentException for an unknown id.
     * - Central guard used by all mutating operations.
     * - O(1).
     */
    private Task requireTask(long taskId) {
        Task task = tasks.get(taskId);
        if (task == null) throw new IllegalArgumentException("Task does not exist: " + taskId);
        return task;
    }

    /**
     * Walks the parent chain upward; a cycle exists if we meet taskId again.
     * - Climbs from the proposed parent toward the root following parentId links.
     * - Returns true if taskId appears in that chain (would form a loop).
     * - Prevents moving a node under itself or one of its descendants.
     * - O(depth) point lookups.
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
     * - Scans all tasks to find the max orderKey among the given parent's children.
     * - Returns max + 1.0, or 0.0 when the parent currently has no children.
     * - O(n) scan (a DB replaces it with an indexed MAX query).
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

    /**
     * Finds the position of the task with the given id within an ordered list.
     * - Linear scan; returns the index, or -1 if not present.
     * - Used to locate a node among its siblings for indent/moveBefore.
     * - O(c) over the list length.
     */
    private static int indexOfById(List<Task> list, long id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == id) return i;
        }
        return -1;
    }
}
