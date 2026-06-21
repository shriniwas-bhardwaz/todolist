package normaltodolistbased;

public class Main {

    /**
     * Demonstrates a full lifecycle of TodoCheckList operations.
     * - creates an empty checklist and appends task1, task2, task4
     * - prints the initial checklist state
     * - inserts task3 before task4 and reprints
     * - marks task2 complete and reprints
     * - moves task3 to index 0 (the beginning) and reprints
     * - deletes task1 and reprints
     * - computes the last valid index (size - 1) and moves task2 there
     * - prints the final state after moving task2 to the end
     */
    public static void main(String[] args) {

        TodoCheckList checklist = new TodoCheckList();

        long task1 = checklist.addTask("task1");
        long task2 = checklist.addTask("task2");
        long task4 = checklist.addTask("task4");

        System.out.println("Initial checklist:");
        System.out.println(checklist.display());

        long task3 = checklist.insertBefore(task4, "task3");

        System.out.println("After inserting task3:");
        System.out.println(checklist.display());

        checklist.markComplete(task2);

        System.out.println("After completing task2:");
        System.out.println(checklist.display());

        checklist.moveTask(task3, 0);

        System.out.println("After moving task3 to the beginning:");
        System.out.println(checklist.display());

        checklist.deleteTask(task1);

        System.out.println("After deleting task1:");
        System.out.println(checklist.display());

        // Scenario: move a task to the LAST position.
        // List currently has size N, so the last index is N-1.
        int lastIndex = checklist.size() - 1;
        System.out.println("Trying to move task2 to the last index (" + lastIndex + ")...");
        checklist.moveTask(task2, lastIndex);

        System.out.println("After moving task2 to the end:");
        System.out.println(checklist.display());
    }
}