package optimized;

import java.util.*;

/**
 * Adjacency-list nested todo list with in-memory indexes for fast reads.
 *
 * Two structures, kept in sync ONLY inside link()/unlink():
 *
 *   tasks            : id -> Task          (O(1) lookup by id)
 *   childrenByParent : parentId -> ordered children list  (O(1) "give me the children")
 *
 * `parentId` on each Task is the authoritative UP-link, used for O(depth) cycle
 * checks and outdent. `childrenByParent` is the DOWN-link/index, giving O(1)
 * sibling access and O(c) local ordering — no full scans, no per-call sorting.
 *
 * Ordering among siblings is just the position in the children list, so there
 * is no fractional orderKey to lose precision.
 *
 * The root level is stored under the map key `null`.
 */
public final class OptimizedNestedTodoList {

    public static final class Task {
        private final long id;
        private String text;
        private boolean completed;
        private Long parentId; // null = root

        private Task(long id, String text, Long parentId) {
            this.id = id;
            this.text = text;
            this.parentId = parentId;
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

    private final Map<Long, Task> tasks = new HashMap<>();
    private final Map<Long, List<Task>> childrenByParent = new HashMap<>();
    private long nextId = 1;

    public OptimizedNestedTodoList() {
        childrenByParent.put(null, new ArrayList<>()); // the root list always exists
    }

    // ----------------------------------------------------------------- create

    /** Adds a task as the last child of parentId (null = last root task). O(1) amortized. */
    public long add(String text, Long parentId) {
        if (parentId != null) requireTask(parentId);
        long id = nextId++;
        Task task = new Task(id, text.trim(), parentId);
        tasks.put(id, task);
        link(task, parentId, siblingList(parentId).size()); // append at end
        return id;
    }

    public long addRootTask(String text)              { return add(text, null); }
    public long addChildTask(long parentId, String t) { return add(t, parentId); }

    // ----------------------------------------------------------------- mutate

    public void setCompleted(long taskId, boolean completed) {
        requireTask(taskId).completed = completed;
    }

    public void updateTask(long taskId, String newText) {
        requireTask(taskId).text = newText.trim();
    }

    /**
     * Moves a task under newParentId (null = root) at the given child index.
     * Cost: O(depth) cycle check + O(c) list edits. The whole subtree follows
     * for free — descendants still sit in childrenByParent under this task.
     */
    public void move(long taskId, Long newParentId, int index) {
        Task task = requireTask(taskId);
        if (newParentId != null) requireTask(newParentId);
        if (createsCycle(taskId, newParentId)) {
            throw new IllegalArgumentException(
                    "A task cannot be moved under itself or one of its descendants");
        }

        List<Task> target = siblingList(newParentId);
        boolean sameList = Objects.equals(task.parentId, newParentId);
        int destSize = target.size() - (sameList ? 1 : 0);
        if (index < 0 || index > destSize) {
            throw new IndexOutOfBoundsException(
                    "Index must be between 0 and " + destSize + ", received: " + index);
        }

        unlink(task);
        link(task, newParentId, index);
    }

    /** Convenience: append under newParentId. */
    public void move(long taskId, Long newParentId) {
        move(taskId, newParentId, siblingListSizeAfterPotentialRemoval(taskId, newParentId));
    }

    /** Reorders/moves a task to sit immediately before beforeTaskId among its siblings. */
    public void moveBefore(long taskId, long beforeTaskId) {
        Task before = requireTask(beforeTaskId);
        List<Task> siblings = siblingList(before.parentId);
        int idx = indexOfById(siblings, beforeTaskId);
        // If we are already an earlier sibling in the same list, removing us first
        // shifts the target left by one.
        Task task = requireTask(taskId);
        boolean sameListBeforeUs =
                Objects.equals(task.parentId, before.parentId)
                        && indexOfById(siblings, taskId) >= 0
                        && indexOfById(siblings, taskId) < idx;
        move(taskId, before.parentId, sameListBeforeUs ? idx - 1 : idx);
    }

    /** Indent: the previous sibling becomes the new parent (appended as its last child). */
    public boolean indent(long taskId) {
        Task task = requireTask(taskId);
        List<Task> siblings = siblingList(task.parentId);
        int idx = indexOfById(siblings, taskId);
        if (idx <= 0) return false;
        Task prev = siblings.get(idx - 1);
        move(taskId, prev.id, siblingList(prev.id).size());
        return true;
    }

    /** Outdent: the task becomes the next sibling of its current parent. */
    public boolean outdent(long taskId) {
        Task task = requireTask(taskId);
        if (task.parentId == null) return false;
        Task parent = tasks.get(task.parentId);
        List<Task> parentSiblings = siblingList(parent.parentId);
        int parentIdx = indexOfById(parentSiblings, parent.id);
        move(taskId, parent.parentId, parentIdx + 1);
        return true;
    }

    /** Deletes a task and its whole subtree. O(subtree size). */
    public void deleteTask(long taskId) {
        Task task = requireTask(taskId);

        Deque<Long> stack = new ArrayDeque<>();
        stack.push(taskId);
        while (!stack.isEmpty()) {
            long id = stack.pop();
            List<Task> kids = childrenByParent.remove(id); // drop this node's child list
            if (kids != null) {
                for (Task child : kids) stack.push(child.id);
            }
            tasks.remove(id);
        }
        siblingList(task.parentId).removeIf(t -> t.id == taskId); // detach from parent
    }

    // ------------------------------------------------------------------ reads

    public Task getTask(long taskId) { return tasks.get(taskId); }

    /** Children of parentId (null = roots), in order. O(1) — returns a read-only view. */
    public List<Task> childrenOf(Long parentId) {
        return Collections.unmodifiableList(siblingList(parentId));
    }

    /** Renders the whole tree. O(n) — single walk, no per-node scans. */
    public String display() {
        StringBuilder out = new StringBuilder();
        appendChildren(null, 0, out);
        return out.toString();
    }

    private void appendChildren(Long parentId, int depth, StringBuilder out) {
        for (Task task : siblingList(parentId)) {
            out.append("    ".repeat(depth))
               .append(task)
               .append(" (id=").append(task.id).append(")")
               .append(System.lineSeparator());
            appendChildren(task.id, depth + 1, out);
        }
    }

    // ----------------------------------------------- index sync (the ONLY choke points)

    /** Attaches task as a child of parentId at the given index, updating both structures. */
    private void link(Task task, Long parentId, int index) {
        task.parentId = parentId;
        siblingList(parentId).add(index, task);
    }

    /** Detaches task from its current parent's child list. */
    private void unlink(Task task) {
        siblingList(task.parentId).removeIf(t -> t.id == task.id);
    }

    /** Returns parentId's child list, creating an empty one on first use. */
    private List<Task> siblingList(Long parentId) {
        return childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>());
    }

    // ---------------------------------------------------------------- helpers

    private Task requireTask(long taskId) {
        Task task = tasks.get(taskId);
        if (task == null) throw new IllegalArgumentException("Task does not exist: " + taskId);
        return task;
    }

    /** Walks the parent chain upward; a cycle exists if we meet taskId again. O(depth). */
    private boolean createsCycle(long taskId, Long proposedParentId) {
        Long current = proposedParentId;
        while (current != null) {
            if (current == taskId) return true;
            current = tasks.get(current).parentId;
        }
        return false;
    }

    private int siblingListSizeAfterPotentialRemoval(long taskId, Long newParentId) {
        Task task = tasks.get(taskId);
        List<Task> target = siblingList(newParentId);
        boolean sameList = task != null && Objects.equals(task.parentId, newParentId);
        return target.size() - (sameList ? 1 : 0);
    }

    private static int indexOfById(List<Task> list, long id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == id) return i;
        }
        return -1;
    }
}
