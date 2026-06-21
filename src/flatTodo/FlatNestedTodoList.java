package flatTodo;

import java.util.*;

public class FlatNestedTodoList {

    public static final class Task {
        private final Long id;
        private String text;
        private boolean completed;
        private int depth;

        private Task(long id, String text,int depth) {
            this.id = id;
            this.text = text;
            this.depth = depth;
        }

        public long getId() {
            return id;
        }

        public String getText() {
            return text;
        }

        public boolean isCompleted() {
            return completed;
        }

        public int getDepth() {
            return depth;
        }

        @Override
        public String toString() {
            return (completed ? "[x] " : "[ ] ") + text;
        }
    }

    private final List<Task> tasks = new ArrayList<>();
    private final Map<Long,Task> taskById = new HashMap<>();
    private long nextId = 1;

    public long addRootTask(String text) {
        Task task = new Task(nextId++,text.trim(),0);
        tasks.add(task);
        taskById.put(task.id,task);
        return task.id;
    }

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

    public void setCompleted(long taskId,boolean completed) {
        Task task = taskById.get(taskId);
        task.completed = completed;
    }

    public void updateTask(long taskId, String text) {
        Task task = taskById.get(taskId);
        task.text = text.trim();
    }

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
     * 1. Extract the task's complete subtree.
     * 2. Remove it from current positon
     * 3. Place it after the old parent's complete subtree.
     * 4. Reduce the depth of every task in the moved subtree
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

    private int findSubtreeEnd(int taskIndex) {
        int taskDepth = tasks.get(taskIndex).depth;
        int index = taskIndex + 1;

        while(index < tasks.size() && tasks.get(index).depth > taskDepth) {
            index++;
        }

        return index;
    }

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

    public List<Task> getTasks() {
        return Collections.unmodifiableList(tasks);
    }

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
