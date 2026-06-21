package nestedtodoTreeList;

import java.util.*;



public class NestedTodoList {

    public static final class Task {
        private final long id;
        private String text;
        private boolean completed;

        /**
         *
         *  we need a parent reference if
         *   - move task to the parent level.
         *   - find siblings
         *   - delete a task from its current parent.
         *   - prevent cycles while moving tasks.
         *   - outdent a task.
         */
        private Task parent;
        private final List<Task> children;

        /**
         * Constructs a tree node (task) with the given identity, label and parent link.
         * - Stores the immutable id and the initial text.
         * - Links the node to its parent (null for a root-level task).
         * - Initializes an empty children list so descendants can be attached later.
         * - O(1).
         */
        public Task(long id, String text, Task parent) {
            this.id = id;
            this.text = text;
            this.parent = parent;
            this.children = new ArrayList<>();
        }

        /**
         * Returns the unique identifier of this task.
         * - Read-only accessor for the immutable id.
         * - O(1).
         */
        public long getId() {
            return id;
        }

        /**
         * Returns the task's display text.
         * - Read-only accessor for the current label.
         * - O(1).
         */
        public String getText() {
            return text;
        }

        /**
         * Replaces the task's display text.
         * - Mutates the label in place; does not touch tree structure.
         * - O(1).
         */
        public void updateText(String text) {
            this.text = text;
        }

        /**
         * Reports whether this task is marked done.
         * - Read-only accessor for the completion flag.
         * - O(1).
         */
        public boolean isCompleted() {
            return completed;
        }

        /**
         * Sets the completion state of this task.
         * - Mutates the done/not-done flag only; children are unaffected.
         * - O(1).
         */
        public void setCompleted(boolean completed) {
            this.completed = completed;
        }

        /**
         * Returns the id of this task's parent, or null if it is a root.
         * - Walks one link up the tree to read the parent's id.
         * - Returns null when there is no parent (root-level node).
         * - O(1).
         */
        public Long getParentId() {
            return parent == null ? null : parent.id;
        }

        /**
         * Returns the direct children of this node.
         * - Exposes the child list wrapped as unmodifiable to protect tree invariants.
         * - Callers cannot mutate structure through the returned view.
         * - O(1) to wrap (no copy).
         */
        public List<Task> getChildren() {
            return Collections.unmodifiableList(children);
        }

        /**
         * Returns this node's parent reference.
         * - Read-only accessor used for upward traversal (siblings, cycle checks, outdent).
         * - Null indicates a root-level task.
         * - O(1).
         */
        public Task getParent() {
            return parent;
        }

        /**
         * Re-links this node to a new parent.
         * - Updates only the upward pointer; the caller is responsible for moving it within the children lists.
         * - O(1).
         */
        public void setParent(Task parent) {
            this.parent = parent;
        }

        /**
         * Renders the task as a checkbox line.
         * - Prefixes "[x] " when completed, otherwise "[ ] ".
         * - Appends the task text.
         * - O(1).
         */
        @Override
        public String toString() {
            return (completed ? "[x] " : "[ ] ") + text;
        }

    }

    private final List<Task> rootTasks = new ArrayList<>();
    private final Map<Long, Task> taskById = new HashMap<>();

    private long nextId = 1;

    /**
     * Inserts a new task as a child of the given parent at a specific position.
     * - Resolves the parent node from parentId (null parentId means insert at root level).
     * - Selects the target child list (root list or parent's children).
     * - Validates index is within [0, list size]; throws IndexOutOfBoundsException otherwise.
     * - Creates a node with the next sequential id and trimmed text, linked to the parent.
     * - Adds the node at the requested index and registers it in the id lookup map.
     * - O(n) due to list insertion shifting elements at the target level.
     */
    public long insertTask(String text, Long parentId, int index) {
        Task parent = parentId == null ? null : taskById.get(parentId);

        List<Task> targetList = getChildrenOf(parent);
        if(index < 0 || index > targetList.size()) {
            throw new IndexOutOfBoundsException("Index must be between 0 and " + targetList.size() + ", received: "+ index);
        }

        Task newTask = new Task(nextId++,text.trim(),parent);
        targetList.add(index,newTask);
        taskById.put(newTask.getId(), newTask);

        return newTask.getId();
    }

    /**
     * Selects the child list a task belongs to within the tree.
     * - Returns the top-level rootTasks list when parent is null.
     * - Otherwise returns the parent's own children list.
     * - Used so root tasks and nested tasks share the same insertion/removal code paths.
     * - O(1).
     */
    private List<Task> getChildrenOf(Task parent) {
        return parent == null ? rootTasks : parent.children;
    }

    /**
     * Appends a new task at the end of the root level.
     * - Delegates to insertTask with a null parent and the current root size as the index.
     * - Resulting node has no parent (depth 0 in the tree).
     * - O(1) append at the root list (amortized).
     */
    public long addRootTask(String text) {
        return insertTask(text,null,rootTasks.size());
    }

    /**
     * Appends a new task as the last child of an existing task.
     * - Looks up the parent node by id.
     * - Delegates to insertTask using the parent's current child count as the index.
     * - O(1) append at the parent's child list (amortized).
     */
    public long addChildTask(long parentId,String text) {
        Task parent = taskById.get(parentId);
        return insertTask(text,parentId,parent.getChildren().size());
    }

    /**
     * Re-parents a task (with its whole subtree) to a new parent and position.
     * - Resolves the task to move and the target parent (null newParentId means move to root level).
     * - Calls validateNoCycle to reject moving a node under itself or a descendant.
     * - Locates the task within its current sibling list; throws if it is missing (corrupt state).
     * - Detects whether the move is within the same list to compute the correct post-removal size.
     * - Validates newIndex (the final index after the move) is within bounds.
     * - No-ops when moving to the same position in the same list.
     * - Removes the node from its old siblings, inserts it at newIndex among new siblings, and updates its parent link.
     * - O(n) due to indexOf and list insertion/removal.
     */
    public void moveTask(long taskId,Long newParentId, int newIndex) {
        Task taskToMove = taskById.get(taskId);
        Task newParent = newParentId== null ? null : taskById.get(newParentId);

        /**
         * Validate the relationship before changing the tree
         */
        validateNoCycle(taskToMove,newParent);
        Task oldParent = taskToMove.getParent();

        List<Task> oldSiblings = getChildrenOf(oldParent);
        List<Task> newSiblings = getChildrenOf(newParent);

        int oldIndex = oldSiblings.indexOf(taskToMove);
        if(oldIndex == -1) {
            throw new IllegalStateException("Task is not present in its parent's child list");
        }

        boolean movingInsideSameList = oldSiblings == newSiblings;

        int destinationSizeAfterRemoval = newSiblings.size() - (movingInsideSameList ? 1 : 0);

        if(newIndex < 0 || newIndex > destinationSizeAfterRemoval) {
            throw new IndexOutOfBoundsException("Index must be between 0 and " + newSiblings.size() + ", received: "+ newIndex);
        }

        if(movingInsideSameList && oldIndex== newIndex) {
            return;
        }

        oldSiblings.remove(oldIndex);
        newSiblings.add(newIndex,taskToMove);
        taskToMove.setParent(newParent);

    }

    /**
     * Guards against creating a cycle when re-parenting.
     * - Walks upward from the proposed parent following parent links to the root.
     * - Throws IllegalArgumentException if taskToMove is encountered on that path (it would become its own ancestor).
     * - Allows the move when the root is reached without finding taskToMove.
     * - O(d) where d is the depth of the proposed parent.
     */
    private void validateNoCycle(Task taskToMove, Task proposedParent)
    {
        Task current = proposedParent;

        while(current != null) {
            if(current == taskToMove) {
                throw new IllegalArgumentException("A task cannot be moved under itself or one of its descendants");
            }
            current = current.getParent();
        }

    }

    /**
     * Indents a task so its immediately preceding sibling becomes its new parent.
     * - Looks up the task and finds its position among its current siblings.
     * - Returns false when the task is first in its list (index <= 0) since there is no previous sibling to host it.
     * - Otherwise picks the previous sibling and moves the task to the end of that sibling's children via moveTask.
     * - Returns true on success.
     * - O(n) driven by the underlying moveTask.
     */
    public boolean indent(long taskId) {
        Task task = taskById.get(taskId);

        List<Task> siblings = getChildrenOf(task.getParent());

        int currentIndex = siblings.indexOf(task);

        /**
         * The first sibling cannot be indented because there is no previous sibling to become its parent
         */
        if(currentIndex <=0) {
            return false;
        }

        Task previousSibling = siblings.get(currentIndex-1);
        int childInsertionIndex = previousSibling.getChildren().size();

        moveTask(taskId,previousSibling.getId(),childInsertionIndex);
        return true;

    }

    /**
     * Outdents a task so it becomes the next sibling of its current parent.
     * - Looks up the task and its parent.
     * - Returns false when the task is already at root level (no parent to move past).
     * - Finds the parent's position within the grandparent's child list.
     * - Moves the task to be inserted right after its old parent (parentIndex + 1) under the grandparent via moveTask.
     * - Returns true on success.
     * - O(n) driven by the underlying moveTask.
     */
    public boolean outdent(long taskId) {
        Task task = taskById.get(taskId);
        Task parentOfTask = task.getParent();

        if(parentOfTask == null) return false;

        Task grandParent = parentOfTask.getParent();
        List<Task> parentSiblings = getChildrenOf(grandParent);
        int parentIndex = parentSiblings.indexOf(parentOfTask);
        moveTask(taskId,parentOfTask.getParentId(),parentIndex+1);
        return true;

    }

    /**
     * Marks a task complete or incomplete by id.
     * - Resolves the node from the id lookup map (O(1)).
     * - Delegates to the node's setCompleted to flip its flag.
     * - O(1).
     */
    public void setCompleted(long taskId, boolean completed) {
        Task task = taskById.get(taskId);
        task.setCompleted(completed);
    }

    /**
     * Renames a task by id.
     * - Resolves the node from the id lookup map (O(1)).
     * - Delegates to the node's updateText to replace its label.
     * - O(1).
     */
    public void updateTask(long taskId, String newText) {

        Task task = taskById.get(taskId);
       task.updateText(newText);
    }

    /**
     * Fetches a task node by its id.
     * - Direct hash-map lookup; returns null if no such task exists.
     * - O(1).
     */
    public Task getTask(long taskId) {
        return taskById.get(taskId);
    }

    /**
     * Returns the top-level tasks of the tree.
     * - Exposes the root list as an unmodifiable view to protect structure.
     * - O(1) to wrap (no copy).
     */
    public List<Task> getRootTasks() {
        return Collections.unmodifiableList(rootTasks);
    }

    /**
     * Builds a human-readable, indented rendering of the whole tree.
     * - Iterates over each root task.
     * - Recursively appends each subtree via appendTask starting at depth 0.
     * - Returns the accumulated multi-line string.
     * - O(n) over all tasks in the tree.
     */
    public String display() {
        StringBuilder output = new StringBuilder();

        for (Task rootTask : rootTasks) {
            appendTask(rootTask, 0, output);
        }

        return output.toString();
    }

    /**
     * Recursively appends one task and its descendants to the output buffer.
     * - Emits four spaces per depth level to visually indent the node.
     * - Appends the task's checkbox text plus its id, then a line separator.
     * - Recurses into each child at depth + 1 (pre-order / depth-first traversal).
     * - O(k) where k is the number of nodes in this subtree.
     */
    private void appendTask(
            Task task,
            int depth,
            StringBuilder output
    ) {
        for (int i = 0; i < depth; i++) {
            output.append("    ");
        }

        output.append(task)
                .append(" (id=")
                .append(task.getId())
                .append(")")
                .append(System.lineSeparator());

        for (Task child : task.getChildren()) {
            appendTask(child, depth + 1, output);
        }
    }


}
