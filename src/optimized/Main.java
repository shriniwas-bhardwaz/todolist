package optimized;

public class Main {

    /**
     * Demo driver exercising the index-based OptimizedNestedTodoList.
     * - Builds a nested tree: two roots plus children/grandchildren under task1.
     * - Prints the initial tree, then completes a deep task (task1.1.1.2).
     * - Moves task1.1 (with its subtree) under task2 as its first child (index 0).
     * - Outdents task1.1 back toward root, then indents it under its previous sibling.
     * - Deletes the task1.1.1 subtree, printing the tree after each step.
     * - Runs two cycle regression checks: move(a, c, 0) and moveBefore(a, c) must both throw.
     * - Prints the final tree.
     */
    public static void main(String[] args) {
        OptimizedNestedTodoList todoList = new OptimizedNestedTodoList();

        long task1 = todoList.addRootTask("task1");
        long task2 = todoList.addRootTask("task2");

        long task11 = todoList.addChildTask(task1, "task1.1");
        todoList.addChildTask(task1, "task1.2");

        long task111 = todoList.addChildTask(task11, "task1.1.1");
        todoList.addChildTask(task111, "task1.1.1.1");
        long task1112 = todoList.addChildTask(task111, "task1.1.1.2");

        System.out.println("Initial tree:");
        System.out.println(todoList.display());

        todoList.setCompleted(task1112, true);
        System.out.println("After completing task1.1.1.2:");
        System.out.println(todoList.display());

        // Move task1.1 (whole subtree) under task2 as first child.
        todoList.move(task11, task2, 0);
        System.out.println("After moving task1.1 under task2:");
        System.out.println(todoList.display());

        todoList.outdent(task11);
        System.out.println("After outdenting task1.1:");
        System.out.println(todoList.display());

        todoList.indent(task11);
        System.out.println("After indenting task1.1 again:");
        System.out.println(todoList.display());

        todoList.deleteTask(task111);
        System.out.println("After deleting task1.1.1 subtree:");
        System.out.println(todoList.display());

        // --- cycle regression checks ---
        long a = todoList.addRootTask("A");
        long b = todoList.addChildTask(a, "B");
        long c = todoList.addChildTask(b, "C");
        try {
            todoList.move(a, c, 0);            // A under its descendant C
            System.out.println("BUG: cycle not rejected (move)");
        } catch (IllegalArgumentException e) {
            System.out.println("move cycle rejected: " + e.getMessage());
        }
        try {
            todoList.moveBefore(a, c);         // A adopts C's parent (B, a descendant of A)
            System.out.println("BUG: cycle not rejected (moveBefore)");
        } catch (IllegalArgumentException e) {
            System.out.println("moveBefore cycle rejected: " + e.getMessage());
        }

        System.out.println("Final tree:");
        System.out.println(todoList.display());
    }
}
