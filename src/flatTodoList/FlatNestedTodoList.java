package flatTodoList;

import java.util.*;

public class FlatNestedTodoList {

    public static final class Task {
        private final Long id;
        private String text;
        private boolean completed;
        private int depth;

        /**
         * Constructs a flat-list task carrying its hierarchy level as a depth integer.
         * - Stores the immutable id and initial text.
         * - Records depth (0 for root) which encodes the tree position in the flat list.
         * - No parent/children pointers: structure is implied by ordering + depth.
         * - O(1).
         */
        private Task(long id, String text,int depth) {
            this.id = id;
            this.text = text;
            this.depth = depth;
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
         * Reports whether this task is marked done.
         * - Read-only accessor for the completion flag.
         * - O(1).
         */
        public boolean isCompleted() {
            return completed;
        }

        /**
         * Returns the task's depth (its nesting level in the flattened tree).
         * - Read-only accessor; 0 means root level, each child is parent depth + 1.
         * - O(1).
         */
        public int getDepth() {
            return depth;
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

    private final List<Task> tasks = new ArrayList<>();
    private final Map<Long,Task> taskById = new HashMap<>();
    private long nextId = 1;

    /**
     * Appends a new root-level task to the end of the flat list.
     * - Creates a node with the next id, trimmed text, and depth 0.
     * - Appends it to the ordered task list and registers it in the id map.
     * - O(1) amortized (list append).
     */
    public long addRootTask(String text) {
        Task task = new Task(nextId++,text.trim(),0);
        tasks.add(task);
        taskById.put(task.id,task);
        return task.id;
    }

    /**
     * Inserts a new task as the last child within the parent's subtree.
     * - Resolves the parent and computes the child depth as parent depth + 1.
     * - Finds the parent's index in the flat list (O(n) via indexOf).
     * - Uses findSubtreeEnd to locate the position just past the parent's existing subtree, so the new child lands after all current descendants.
     * - Inserts the child at that index (shifting later elements) and registers it in the id map.
     * - O(n) overall (indexOf + subtree scan + list insertion).
     */
    public long addChildTask(long parentId, String text) {
        Task parentTask = taskById.get(parentId);
        int depth = parentTask.depth;
        int parentIndex = tasks.indexOf(parentTask);
        int insertionIndex = findSubtreeEnd(parentIndex);
        Task childTask = new Task(nextId++,text.trim(),depth + 1);
        tasks.add(insertionIndex,childTask);
        taskById.put(childTask.id, childTask);

        return childTask.id;

    }

    /**
     * Marks a task complete or incomplete by id.
     * - Resolves the node from the id map (O(1)) and sets its completed flag.
     * - Does not affect descendants in the flat list.
     * - O(1).
     */
    public void setCompleted(long taskId,boolean completed) {
        Task task = taskById.get(taskId);
        task.completed = completed;
    }

    /**
     * Renames a task by id.
     * - Resolves the node from the id map (O(1)) and replaces its text with the trimmed value.
     * - O(1).
     */
    public void updateTask(long taskId, String text) {
        Task task = taskById.get(taskId);
        task.text = text.trim();
    }

    /**
     * Deletes a task together with its entire subtree from the flat list.
     * - Resolves the node (O(1)) and finds its start index in the list (O(n)).
     * - Uses findSubtreeEnd to find the exclusive end index covering all deeper-depth descendants.
     * - Unregisters every node in [startIndex, endIndex) from the id map (O(s) for subtree size s).
     * - Clears that contiguous range from the list in one sublist operation.
     * - O(n) overall.
     */
    public void deleteTask(long taskId) {
        Task task = taskById.get(taskId);   // O(1)
        int startIndex = tasks.indexOf(task);  // O(n)

        int endIndex = findSubtreeEnd(startIndex); // O(s) - scans the subtree

        for(int i=startIndex;i< endIndex;i++) {
            taskById.remove(tasks.get(i).id);  // one O(1) removal per subtree node so O(s)
        }
        tasks.subList(startIndex,endIndex).clear();  // O(n)

        //total time complexity - O(n)
    }

    /**
     * Indents a task and its subtree by making it a child of its previous sibling.
     * - Resolves the node and its index in the flat list (O(n)).
     * - Looks for a previous sibling at the same depth; returns false if none exists (cannot indent the first sibling).
     * - Determines the subtree range via findSubtreeEnd and increments the depth of every node in it.
     * - List order is unchanged; only depth values shift, which implicitly re-parents under the prior sibling.
     * - Returns true on success.
     * - O(n) (indexOf + sibling search + subtree depth bump).
     */
    public boolean indent(long taskId) {
        Task task = taskById.get(taskId);
        int taskIndex = tasks.indexOf(task);

        int previousSibling = findPreviousSibling(taskIndex);
        if(previousSibling == -1) return false;

        int subtreeEnd = findSubtreeEnd(taskIndex);
        for(int i=taskIndex; i <subtreeEnd;i++) {
            tasks.get(i).depth++;
        }

        return true;
    }

    /**
     * Outdents a task and its subtree so it becomes a sibling of its former parent.
     * - Resolves the node and its index; returns false if already at depth 0 (nothing to outdent to).
     * - Finds the parent index and the end of the parent's full subtree (findParentIndex / findSubtreeEnd).
     * - Extracts the task's complete subtree into a temporary list.
     * - Removes that subtree from its current position in the flat list.
     * - Computes the insertion index just after the old parent's remaining subtree (parentSubTreeEnd - subTreeSize).
     * - Decrements the depth of every node in the moved subtree by one (promoting it one level).
     * - Re-inserts the subtree contiguously at the computed index, preserving internal order.
     * - Returns true on success.
     * - O(n) (index scans plus sublist extraction and re-insertion).
     */
    public boolean outdent(long taskId) {
        Task task = taskById.get(taskId);
        int taskIndex = tasks.indexOf(task);



        if(task.depth == 0) {
            return false;
        }

        int parentIndex = findParentIndex(taskIndex);
        int parentSubTreeEnd = findSubtreeEnd(parentIndex);
        int taskSubTreeEnd = findSubtreeEnd(taskIndex);

        List<Task> subTree = new ArrayList<>(tasks.subList(taskIndex,taskSubTreeEnd));
        int subTreeSize = subTree.size();

        tasks.subList(taskIndex,taskSubTreeEnd).clear();
        int insertionIndex = parentSubTreeEnd - subTreeSize ;

        for (Task t : subTree) {
            t.depth--;
        }

        tasks.addAll(insertionIndex, subTree);

        return true;



    }

    /**
     * Computes the exclusive end index of the subtree rooted at the given index.
     * - Reads the root node's depth.
     * - Scans forward while subsequent nodes have a strictly greater depth (they are descendants).
     * - Stops at the first node whose depth is <= the root's depth (the next sibling or shallower node), or at end of list.
     * - Returns that stop index, so [taskIndex, result) covers the whole subtree.
     * - O(s) where s is the subtree size.
     */
    private int findSubtreeEnd(int taskIndex) {
        int taskDepth = tasks.get(taskIndex).depth;
        int index = taskIndex + 1;

        while(index < tasks.size() && tasks.get(index).depth > taskDepth) {
            index++;
        }

        return index;
    }

    /**
     * Finds the index of the nearest preceding sibling at the same depth.
     * - Reads the current node's depth.
     * - Scans backward from the node toward the start of the list.
     * - Returns the first earlier index whose depth equals the current depth (a sibling).
     * - Returns -1 if it first hits a node with smaller depth (the parent boundary) or runs off the start, meaning no previous sibling exists.
     * - O(n) worst case backward scan.
     */
    private int findPreviousSibling(int taskIndex) {
        int currentDepth = tasks.get(taskIndex).depth;

        for(int i= taskIndex - 1; i>=0;i--) {
            int candidateDepth = tasks.get(i).depth;

            if(candidateDepth == currentDepth) {
                return i;
            }

            if(candidateDepth < currentDepth) {
                return  -1;
            }
        }

        return -1;
    }

    /**
     * Finds the index of the immediate parent of the node at the given index.
     * - Reads the node's depth; returns -1 if it is a root (depth 0, no parent).
     * - Scans backward looking for the nearest earlier node at depth currentDepth - 1.
     * - Returns that index as the parent.
     * - Throws IllegalStateException if no such ancestor is found (corrupt hierarchy).
     * - O(n) worst case backward scan.
     */
    private int findParentIndex(int taskIndex) {
        int currentDepth = tasks.get(taskIndex).depth;
        if(currentDepth == 0) return -1;
        for(int i= taskIndex-1; i>=0; i--) {
            int candidateDepth = tasks.get(i).depth;

            if(candidateDepth == currentDepth -1)
                return i;
        }
        throw new IllegalStateException("Invalid hierarchy");
    }

    /**
     * Returns the full flattened task list in display order.
     * - Exposes the ordered list as an unmodifiable view to protect structure.
     * - O(1) to wrap (no copy).
     */
    public List<Task> getTasks() {
        return Collections.unmodifiableList(tasks);
    }

    /**
     * Builds a human-readable, indented rendering of the flattened tree.
     * - Iterates the flat list in order (which is already a depth-first layout).
     * - Indents each line by four spaces per depth level using the node's depth field.
     * - Appends the checkbox text plus the id, then a line separator.
     * - Returns the accumulated multi-line string.
     * - O(n) over all tasks.
     */
    public String display() {
        StringBuilder output = new StringBuilder();

        for (Task task : tasks) {
            output.append("    ".repeat(task.depth))
                    .append(task)
                    .append(" (id=")
                    .append(task.id)
                    .append(")")
                    .append(System.lineSeparator());
        }

        return output.toString();
    }


}
