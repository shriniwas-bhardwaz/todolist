package nestedtodo;

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

        public Task(long id, String text, Task parent) {
            this.id = id;
            this.text = text;
            this.parent = parent;
            this.children = new ArrayList<>();
        }

        public long getId() {
            return id;
        }

        public String getText() {
            return text;
        }

        public void updateText(String text) {
            this.text = text;
        }

        public boolean isCompleted() {
            return completed;
        }

        public void setCompleted(boolean completed) {
            this.completed = completed;
        }

        public Long getParentId() {
            return parent == null ? null : parent.id;
        }

        public List<Task> getChildren() {
            return Collections.unmodifiableList(children);
        }

        public Task getParent() {
            return parent;
        }

        public void setParent(Task parent) {
            this.parent = parent;
        }

        @Override
        public String toString() {
            return (completed ? "[x] " : "[ ] ") + text;
        }

    }

    private final List<Task> rootTasks = new ArrayList<>();
    private final Map<Long, Task> taskById = new HashMap<>();

    private long nextId = 1;

    /**
     * Inserts a new task.
     *
     * parentId == null means root level.
     * index is zero-based.
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
     *  Returns rootTasks when parent is null
     *  Otherwise return the parent's child list
     */
    private List<Task> getChildrenOf(Task parent) {
        return parent == null ? rootTasks : parent.children;
    }

    public long addRootTask(String text) {
        return insertTask(text,null,rootTasks.size());
    }

    public long addChildTask(long parentId,String text) {
        Task parent = taskById.get(parentId);
        return insertTask(text,parentId,parent.getChildren().size());
    }

    /**
     * Moes a task to another parent and position
     *  newParentId == null means move to the root level.
     *
     *  newIndex is the final index after the task has been moved
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
     * Prevents moving a task under itself or its descendant.
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
     *  Indent a task.
     *  Its previous sibling becomes its new parent.
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
     * Outdents a task.
     *
     * The task becomes the next sibling of its current parent.
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

    public void setCompleted(long taskId, boolean completed) {
        Task task = taskById.get(taskId);
        task.setCompleted(completed);
    }

    public void updateTask(long taskId, String newText) {

        Task task = taskById.get(taskId);
       task.updateText(newText);
    }

    public Task getTask(long taskId) {
        return taskById.get(taskId);
    }

    public List<Task> getRootTasks() {
        return Collections.unmodifiableList(rootTasks);
    }

    public String display() {
        StringBuilder output = new StringBuilder();

        for (Task rootTask : rootTasks) {
            appendTask(rootTask, 0, output);
        }

        return output.toString();
    }

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
