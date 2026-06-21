package normaltodo;

public class TodoLinkedListMain {

    public static void main(String[] args) {

        TodoCheckList checklist = new TodoCheckList();

        long task1 = checklist.addTask("task1");
        long task2 = checklist.addTask("task2");
        long task4 = checklist.addTask("task4");

        System.out.println("Initial checklist:");
        System.out.println(checklist.display());

        long task3 = checklist.insertBefore(task4, "task3");

        System.out.println("After inserting task3 before task4:");
        System.out.println(checklist.display());

        checklist.insertAfter(task1, "task1.5");

        System.out.println("After inserting after task1:");
        System.out.println(checklist.display());

        checklist.markComplete(task2);

        checklist.deleteTask(task3);

        System.out.println("After completing task2 and deleting task3:");
        System.out.println(checklist.display());
    }
}