package nestedtodo;

public class Main {

    public static void main(String[] args) {
        NestedTodoList todoList = new NestedTodoList();

        long task1 = todoList.addRootTask("task1");
        long task2 = todoList.addRootTask("task2");

        long task11 =
                todoList.addChildTask(task1, "task1.1");

        todoList.addChildTask(task1, "task1.2");

        long task111 =
                todoList.addChildTask(task11, "task1.1.1");

        todoList.addChildTask(task111, "task1.1.1.1");
        todoList.addChildTask(task111, "task1.1.1.2");

        System.out.println("Initial tree:");
        System.out.println(todoList.display());

        /*
         * Move task1.1 under task2.
         */
        todoList.moveTask(task11, task2, 0);

        System.out.println("After moving task1.1 under task2:");
        System.out.println(todoList.display());
    }
}
