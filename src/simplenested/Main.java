package simplenested;

public class Main {

    public static void main(String[] args) {
        SimpleNestedTodoList todoList = new SimpleNestedTodoList();

        long task1 = todoList.addRootTask("task1");
        long task2 = todoList.addRootTask("task2");

        long task11 = todoList.addChildTask(task1, "task1.1");
        todoList.addChildTask(task1, "task1.2");

        long task111 = todoList.addChildTask(task11, "task1.1.1");
        todoList.addChildTask(task111, "task1.1.1.1");
        long task1112 = todoList.addChildTask(task111, "task1.1.1.2");

        System.out.println("Initial tree:");
        System.out.println(todoList.display());

        // Complete a deep task.
        todoList.setCompleted(task1112, true);
        System.out.println("After completing task1.1.1.2:");
        System.out.println(todoList.display());

        // Move task1.1 (and its whole subtree) under task2 — one assignment.
        todoList.move(task11, task2);
        System.out.println("After moving task1.1 under task2:");
        System.out.println(todoList.display());

        // Outdent task1.1 back to root level.
        todoList.outdent(task11);
        System.out.println("After outdenting task1.1:");
        System.out.println(todoList.display());

        // Indent task1.1 again under its previous sibling.
        todoList.indent(task11);
        System.out.println("After indenting task1.1 again:");
        System.out.println(todoList.display());

        // Delete task1.1.1 and its subtree.
        todoList.deleteTask(task111);
        System.out.println("After deleting task1.1.1 subtree:");
        System.out.println(todoList.display());

        // Cycle prevention: moving task1 under its own descendant must throw.
        try {
            todoList.move(task1, task11);
        } catch (IllegalArgumentException e) {
            System.out.println("Cycle correctly rejected: " + e.getMessage());
        }
    }
}
