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

        /**
         * Constructs a single tree node.
         * - Stores immutable id plus mutable text/parentId (no orderKey here — order
         *   comes from position in the childrenByParent list instead).
         * - parentId is the authoritative UP-link (null = root).
         * - Private: nodes are only created via the enclosing list's add().
         * - O(1).
         */
        private Task(long id, String text, Long parentId) {
            this.id = id;
            this.text = text;
            this.parentId = parentId;
        }

        /**
         * Returns this node's stable unique id.
         * - Key into the tasks map and parentId of its children.
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
         * - Per-node flag; does not cascade to children.
         * - O(1).
         */
        public boolean isCompleted() { return completed; }
        /**
         * Returns the id of this node's parent, or null if it is a root.
         * - The UP-link used for O(depth) cycle checks and outdent.
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

    private final Map<Long, Task> tasks = new HashMap<>();
    private final Map<Long, List<Task>> childrenByParent = new HashMap<>();
    private long nextId = 1;

    /**
     * Constructs an empty list with its two in-memory indexes ready.
     * - Seeds the childrenByParent map with an empty root list under key null.
     * - Guarantees the root sibling list always exists before any add().
     * - O(1).
     */
    public OptimizedNestedTodoList() {
        childrenByParent.put(null, new ArrayList<>()); // the root list always exists
    }

    // ----------------------------------------------------------------- create

    /**
     * Adds a task as the last child of parentId (null = last root task).
     * - Validates the parent exists (null root parent is allowed).
     * - Allocates the next id, builds the node, and stores it in the tasks map.
     * - Links it at the end of the parent's child list, keeping both indexes in sync.
     * - Returns the new task's id.
     * - O(1) amortized (no scans or sorts).
     */
    public long add(String text, Long parentId) {
        if (parentId != null) requireTask(parentId);
        long id = nextId++;
        Task task = new Task(id, text.trim(), parentId);
        tasks.put(id, task);
        link(task, parentId, siblingList(parentId).size()); // append at end
        return id;
    }

    /**
     * Convenience wrapper to add a top-level (root) task.
     * - Delegates to add() with a null parent.
     * - O(1) amortized.
     */
    public long addRootTask(String text)              { return add(text, null); }
    /**
     * Convenience wrapper to add a child under an existing task.
     * - Delegates to add() with the given parentId.
     * - O(1) amortized.
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
     * - Tree structure is unchanged.
     * - O(1).
     */
    public void updateTask(long taskId, String newText) {
        requireTask(taskId).text = newText.trim();
    }

    /**
     * Moves a task under newParentId (null = root) at the given child index.
     * - Validates the task and target parent exist, and rejects cycle-forming moves.
     * - Bounds-checks index against the destination size (adjusting for same-list moves).
     * - Unlinks from the old parent's list, then links into the new one at index.
     * - The whole subtree follows for free (descendants stay under this task's id).
     * - O(depth) cycle check + O(c) list edits.
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

    /**
     * Convenience overload: append the task as the last child of newParentId.
     * - Computes the end index, accounting for the task's own removal if it is
     *   already in the target list, then delegates to the indexed move().
     * - O(depth) cycle check + O(c) list edits.
     */
    public void move(long taskId, Long newParentId) {
        move(taskId, newParentId, siblingListSizeAfterPotentialRemoval(taskId, newParentId));
    }

    /**
     * Reorders/moves a task to sit immediately before beforeTaskId among its siblings.
     * - Targets the reference task's parent list and finds the reference's index.
     * - If the task already sits earlier in that same list, decrements the target index
     *   by one to compensate for its own removal during the move.
     * - Delegates to the indexed move() (which performs the cycle check).
     * - O(depth) cycle check + O(c) list edits/scans.
     */
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

    /**
     * Indent: the previous sibling becomes the new parent (appended as its last child).
     * - Finds the task's index among its siblings; returns false if it is first.
     * - Otherwise moves it to the end of the preceding sibling's child list.
     * - O(depth) cycle check + O(c) list edits.
     */
    public boolean indent(long taskId) {
        Task task = requireTask(taskId);
        List<Task> siblings = siblingList(task.parentId);
        int idx = indexOfById(siblings, taskId);
        if (idx <= 0) return false;
        Task prev = siblings.get(idx - 1);
        move(taskId, prev.id, siblingList(prev.id).size());
        return true;
    }

    /**
     * Outdent: the task becomes the next sibling of its current parent.
     * - Returns false if the task is already at root level.
     * - Otherwise inserts it into the grandparent's list right after its parent's slot,
     *   promoting it one level up the tree (subtree following along).
     * - O(depth) cycle check + O(c) list edits.
     */
    public boolean outdent(long taskId) {
        Task task = requireTask(taskId);
        if (task.parentId == null) return false;
        Task parent = tasks.get(task.parentId);
        List<Task> parentSiblings = siblingList(parent.parentId);
        int parentIdx = indexOfById(parentSiblings, parent.id);
        move(taskId, parent.parentId, parentIdx + 1);
        return true;
    }

    /**
     * Deletes a task and its whole subtree.
     * - Validates the subtree root exists.
     * - Walks downward with an explicit stack, removing each node's child list and
     *   the node itself from both indexes (using the O(1) childrenByParent down-links).
     * - Finally detaches the root from its parent's child list.
     * - O(subtree size) — no full scans, unlike the simple version.
     */
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

    /**
     * Fetches a single task by id.
     * - Direct map lookup; returns null if the id is unknown (no exception).
     * - O(1).
     */
    public Task getTask(long taskId) { return tasks.get(taskId); }

    /**
     * Children of parentId (null = roots), in order.
     * - Returns the maintained child list directly, wrapped read-only.
     * - Order is the list position; no scan or sort needed.
     * - O(1) — returns a read-only view.
     */
    public List<Task> childrenOf(Long parentId) {
        return Collections.unmodifiableList(siblingList(parentId));
    }

    /**
     * Renders the entire tree as an indented, human-readable string.
     * - Starts from the roots and recursively appends each subtree depth-first.
     * - Indentation depth reflects nesting level.
     * - O(n) — single walk, no per-node scans (uses the down-link index).
     */
    public String display() {
        StringBuilder out = new StringBuilder();
        appendChildren(null, 0, out);
        return out.toString();
    }

    /**
     * Recursive helper that writes one level of the tree and descends into each child.
     * - Prepends depth-proportional indentation, then the task and its id.
     * - Recurses on each child via the O(1) sibling-list index.
     * - O(n) across the full tree.
     */
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

    /**
     * Attaches task as a child of parentId at the given index, updating both structures.
     * - Sets the node's UP-link (parentId) and inserts it into the DOWN-link list.
     * - One of only two choke points keeping the two indexes consistent.
     * - O(c) for the list insert.
     */
    private void link(Task task, Long parentId, int index) {
        task.parentId = parentId;
        siblingList(parentId).add(index, task);
    }

    /**
     * Detaches task from its current parent's child list.
     * - Removes it from the DOWN-link list by id (UP-link is reset by the next link()).
     * - The other choke point that keeps the indexes consistent.
     * - O(c) for the list scan/remove.
     */
    private void unlink(Task task) {
        siblingList(task.parentId).removeIf(t -> t.id == task.id);
    }

    /**
     * Returns parentId's child list, creating an empty one on first use.
     * - Lazily initializes the entry in childrenByParent (root is key null).
     * - Guarantees callers always get a mutable list back.
     * - O(1).
     */
    private List<Task> siblingList(Long parentId) {
        return childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>());
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
     * - Climbs from the proposed parent toward the root via parentId UP-links.
     * - Returns true if taskId appears in that chain (would form a loop).
     * - Prevents moving a node under itself or one of its descendants.
     * - O(depth).
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
     * Computes the destination list size as it would be after removing the task.
     * - If the task already lives in the target list, subtracts one for its removal.
     * - Used by the append-overload of move() to compute the correct end index.
     * - O(1).
     */
    private int siblingListSizeAfterPotentialRemoval(long taskId, Long newParentId) {
        Task task = tasks.get(taskId);
        List<Task> target = siblingList(newParentId);
        boolean sameList = task != null && Objects.equals(task.parentId, newParentId);
        return target.size() - (sameList ? 1 : 0);
    }

    /**
     * Finds the position of the task with the given id within an ordered list.
     * - Linear scan; returns the index, or -1 if not present.
     * - Used to locate a node among its siblings for indent/outdent/moveBefore.
     * - O(c) over the list length.
     */
    private static int indexOfById(List<Task> list, long id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == id) return i;
        }
        return -1;
    }
}
