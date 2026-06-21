package normaltodo;

import java.util.*;

public final class TodoCheckList {

    private final List<TodoItem> tasks;

    private final Map<Long, TodoItem> tasksById;

    private long nextId;


    public TodoCheckList() {
        this.tasks = new ArrayList<>();
        this.tasksById = new HashMap<>();
        this.nextId = 1;
    }

    /**
     * Adds a task at the end of the checklist
     */
    public long addTask(String text) {
        return insertTask(tasks.size(),text);
    }

    public long insertTask(int index, String text) {
        long taskId = nextId++;
        TodoItem task = new TodoItem(taskId,text);

        tasks.add(index,task);
        tasksById.put(taskId,task);

        return taskId;
    }

    /**
     *  Inserts a task immediately before another task.
     */
    public long insertBefore(long existingTaskId, String text) {
        int existingTaskIndex = findTaskIndex(existingTaskId);
        return insertTask(existingTaskIndex,text);
    }

    /**
     *  Inserts a task immediately after another task
     */
    public long insertAfter(long existingTaskId, String text) {
        int existingTaskIndex = findTaskIndex(existingTaskId);
        return insertTask(existingTaskIndex+1,text);
    }

    /**
     *  Update the task text
     */
    public void updateTask(long taskId, String newText) {
        TodoItem task = getRequiredTask(taskId);
        task.updateText(newText.trim());
    }

    /**
     *  Marks the task as complete.
     */
    public void markComplete(long taskId) {
        setCompletionStatus(taskId,true);
    }

    /**
     *  Marks the task as incomplete
     */
    public void markIncomplete(long taskId) {
        setCompletionStatus(taskId,false);
    }


    public void setCompletionStatus(long taskId, boolean completed) {
        TodoItem task = getRequiredTask(taskId);
        task.setCompleted(completed);
    }

    /**
     *  Togggles complete to incomplete or incomplete to complete.
     */
    public void toggleTask(long taskId) {
        TodoItem task = getRequiredTask(taskId);
        task.setCompleted(!task.isCompleted());
    }


    /**
     * Deletes a task.
     */
    public void deleteTask(long taskId) {
        TodoItem task = getRequiredTask(taskId);

        tasks.remove(task);
        tasksById.remove(taskId);
    }

    /**
     * Moves an existing task to a different position.
     *
     */
    public void moveTask(long taskId, int newIndex) {
        TodoItem task = getRequiredTask(taskId);

        if(newIndex <0 || newIndex>= tasks.size()) {
            throw new IndexOutOfBoundsException("New index must be between 0 and " + (tasks.size() - 1));
        }

        int currentIndex = tasks.indexOf(task);
        if(currentIndex == newIndex) return ;

        tasks.remove(task);
        tasks.add(newIndex,task);
    }

    private TodoItem getRequiredTask(long taskId) {
        TodoItem task = tasksById.get(taskId);  // o(1)

        if(task == null) {
            throw new IllegalArgumentException("Task does not exist: " + taskId);
        }
        return task;
    }

    private int findTaskIndex(long taskId) {
        TodoItem task = getRequiredTask(taskId);
        return tasks.indexOf(task);
    }

    public int size() {
        return tasks.size();
    }

    public List<TodoItem> getTasks() {
        return Collections.unmodifiableList(tasks);
    }

    public String display() {
        StringBuilder output = new StringBuilder();

        for (TodoItem task : tasks) {
            output.append(task)
                    .append(" (id=")
                    .append(task.getId())
                    .append(")")
                    .append(System.lineSeparator());
        }

        return output.toString();
    }
}
