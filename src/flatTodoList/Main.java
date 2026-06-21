package flatTodoList;

public class Main {

    /**
     * Demonstrates the flat-list (depth-encoded) FlatNestedTodoList with a sample scenario.
     * - Creates two root tasks (task1, task2).
     * - Builds a nested hierarchy under task1: task1.1, task1.2, then task1.1.1 with two leaves, plus task2.1 under task2.
     * - Prints the initial hierarchy.
     * - Marks task1.1.1.2 completed and prints the updated view.
     * - (The moveTask reordering steps are commented out, so they are skipped.)
     * - Outdents task1.1 (promoting its subtree to root level after task2) and prints the result.
     * - Indents task1.1 again so its previous sibling becomes its parent, then prints.
     * - Deletes the task1.1.1 subtree and prints the final hierarchy.
     */
    public static void main(String[] args) {

        FlatNestedTodoList todoList =
                new FlatNestedTodoList();

        long task1 =
                todoList.addRootTask("task1");

        long task2 =
                todoList.addRootTask("task2");

        long task11 =
                todoList.addChildTask(
                        task1,
                        "task1.1"
                );

        long task12 =
                todoList.addChildTask(
                        task1,
                        "task1.2"
                );

        long task111 =
                todoList.addChildTask(
                        task11,
                        "task1.1.1"
                );

        long task1111 =
                todoList.addChildTask(
                        task111,
                        "task1.1.1.1"
                );

        long task1112 =
                todoList.addChildTask(
                        task111,
                        "task1.1.1.2"
                );

        long task21 =
                todoList.addChildTask(
                        task2,
                        "task2.1"
                );

        System.out.println("Initial hierarchy:");
        System.out.println(todoList.display());

        /*
         * Test completion.
         */
        todoList.setCompleted(task1112, true);

        System.out.println("After completing task1.1.1.2:");
        System.out.println(todoList.display());

        /*
         * Move task1.1 under task2 as its first child.
         *
         * The complete subtree:
         *
         * task1.1
         *   task1.1.1
         *     task1.1.1.1
         *     task1.1.1.2
         *
         * moves together.
         */
//        todoList.moveTask(
//                task11,
//                task2,
//                0
//        );

        System.out.println(
                "After moving task1.1 under task2:"
        );
        System.out.println(todoList.display());

        /*
         * task2 currently has:
         *
         * task1.1
         * task2.1
         *
         * Move task2.1 to child index 0 under the same parent.
         */
//        todoList.moveTask(
//                task21,
//                task2,
//                0
//        );

        System.out.println(
                "After reordering children under task2:"
        );
        System.out.println(todoList.display());

        /*
         * Outdent task1.1.
         *
         * It becomes a root task immediately after task2.
         */
        todoList.outdent(task11);

        System.out.println("After outdenting task1.1:");
        System.out.println(todoList.display());

        /*
         * Indent task1.1 again.
         *
         * Its previous root sibling, task2,
         * becomes its parent.
         */
        todoList.indent(task11);

        System.out.println("After indenting task1.1 again:");
        System.out.println(todoList.display());

        /*
         * Delete task1.1.1 and its complete subtree.
         */
        todoList.deleteTask(task111);

        System.out.println(
                "After deleting task1.1.1 subtree:"
        );
        System.out.println(todoList.display());
    }
}
