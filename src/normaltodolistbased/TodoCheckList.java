package normaltodolistbased;

import java.util.*;

public final class TodoCheckList {

    private final List<TodoItem> tasks;

    private final Map<Long, TodoItem> tasksById;

    private long nextId;


    /**
     * Constructs an empty checklist.
     * - creates a backing ArrayList to hold tasks in order
     * - creates a HashMap for O(1) lookup of tasks by id
     * - initializes the id generator (nextId) to 1
     * - O(1) initialization
     */
    public TodoCheckList() {
        this.tasks = new ArrayList<>();
        this.tasksById = new HashMap<>();
        this.nextId = 1;
    }

    /**
     * Adds a task at the end of the checklist.
     * - delegates to insertTask using the current list size as the index
     * - inserting at index == size() appends to the tail
     * - returns the generated id of the new task
     * - O(1) amortized (append to ArrayList)
     */
    public long addTask(String text) {
        return insertTask(tasks.size(),text);
    }

    /**
     * Inserts a new task at a specific index in the checklist.
     * - allocates the next sequential id and advances the counter
     * - creates a new TodoItem with that id and the given text
     * - inserts the item into the ordered list at the requested index
     * - registers the item in the id-to-task map for fast lookup
     * - returns the generated id
     * - O(n) because ArrayList.add(index, ...) shifts subsequent elements
     */
    public long insertTask(int index, String text) {
        long taskId = nextId++;
        TodoItem task = new TodoItem(taskId,text);

        tasks.add(index,task);
        tasksById.put(taskId,task);

        return taskId;
    }

    /**
     * Inserts a task immediately before another existing task.
     * - looks up the index of the existing task (throws if it does not exist)
     * - inserts the new task at that same index, pushing the existing task down
     * - returns the new task's id
     * - O(n) due to index lookup and list shifting
     */
    public long insertBefore(long existingTaskId, String text) {
        int existingTaskIndex = findTaskIndex(existingTaskId);
        return insertTask(existingTaskIndex,text);
    }

    /**
     * Inserts a task immediately after another existing task.
     * - looks up the index of the existing task (throws if it does not exist)
     * - inserts the new task at index + 1 (right after the existing task)
     * - returns the new task's id
     * - O(n) due to index lookup and list shifting
     */
    public long insertAfter(long existingTaskId, String text) {
        int existingTaskIndex = findTaskIndex(existingTaskId);
        return insertTask(existingTaskIndex+1,text);
    }

    /**
     * Updates the text of an existing task.
     * - resolves the task by id (throws if it does not exist)
     * - trims surrounding whitespace from the new text before storing
     * - replaces the task's description with the trimmed text
     * - O(1) map lookup plus O(1) update
     */
    public void updateTask(long taskId, String newText) {
        TodoItem task = getRequiredTask(taskId);
        task.updateText(newText.trim());
    }

    /**
     * Marks the task as complete.
     * - delegates to setCompletionStatus with completed = true
     * - O(1) map lookup plus O(1) flag update
     */
    public void markComplete(long taskId) {
        setCompletionStatus(taskId,true);
    }

    /**
     * Marks the task as incomplete.
     * - delegates to setCompletionStatus with completed = false
     * - O(1) map lookup plus O(1) flag update
     */
    public void markIncomplete(long taskId) {
        setCompletionStatus(taskId,false);
    }


    /**
     * Sets the completion status of a task to an explicit value.
     * - resolves the task by id (throws if it does not exist)
     * - applies the provided completed boolean to the task
     * - O(1) map lookup plus O(1) flag update
     */
    public void setCompletionStatus(long taskId, boolean completed) {
        TodoItem task = getRequiredTask(taskId);
        task.setCompleted(completed);
    }

    /**
     * Toggles a task between complete and incomplete.
     * - resolves the task by id (throws if it does not exist)
     * - reads the current completion flag and writes its negation
     * - O(1) map lookup plus O(1) flag update
     */
    public void toggleTask(long taskId) {
        TodoItem task = getRequiredTask(taskId);
        task.setCompleted(!task.isCompleted());
    }


    /**
     * Deletes a task from the checklist.
     * - resolves the task by id (throws if it does not exist)
     * - removes the item from the ordered list
     * - removes the item from the id-to-task lookup map
     * - O(n) because list removal scans/shifts elements
     */
    public void deleteTask(long taskId) {
        TodoItem task = getRequiredTask(taskId);

        tasks.remove(task);
        tasksById.remove(taskId);
    }

    /**
     * Moves an existing task to a different position in the list.
     * - resolves the task by id (throws if it does not exist)
     * - validates newIndex is within [0, size-1], else throws IndexOutOfBoundsException
     * - returns early (no-op) if the task is already at the target index
     * - otherwise removes the task and re-inserts it at the new index
     * - O(n) due to index lookup and element shifting
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

    /**
     * Looks up a task by id, requiring that it exists.
     * - fetches the task from the id-to-task map
     * - throws IllegalArgumentException when no task matches the id
     * - returns the resolved task otherwise
     * - O(1) HashMap lookup
     */
    private TodoItem getRequiredTask(long taskId) {
        TodoItem task = tasksById.get(taskId);  // o(1)

        if(task == null) {
            throw new IllegalArgumentException("Task does not exist: " + taskId);
        }
        return task;
    }

    /**
     * Finds the positional index of a task within the ordered list.
     * - resolves the task by id (throws if it does not exist)
     * - scans the list to find that task's index
     * - O(n) due to the linear indexOf scan
     */
    private int findTaskIndex(long taskId) {
        TodoItem task = getRequiredTask(taskId);
        return tasks.indexOf(task);
    }

    /**
     * Returns the number of tasks in the checklist.
     * - delegates to the backing list's size
     * - O(1) for ArrayList
     */
    public int size() {
        return tasks.size();
    }

    /**
     * Returns a read-only view of the tasks.
     * - wraps the internal list in an unmodifiable view
     * - prevents callers from mutating the checklist directly
     * - O(1) wrapper creation (no copy made)
     */
    public List<TodoItem> getTasks() {
        return Collections.unmodifiableList(tasks);
    }

    /**
     * Builds a printable string of all tasks in order.
     * - iterates over the tasks in their list order
     * - appends each task's toString() followed by " (id=<id>)"
     * - terminates each line with the platform line separator
     * - returns the accumulated multi-line string
     * - O(n) over the number of tasks
     */
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
