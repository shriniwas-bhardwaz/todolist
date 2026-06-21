package normaltodolistbased;

import java.util.HashMap;
import java.util.Map;

public final class TodoCheckLinkedList {

    private static final class Node {

        private final TodoItem task;
        private Node next;
        private Node prev;

        /**
         * Constructs a doubly-linked-list node wrapping a task.
         * - stores the supplied task as the node's payload
         * - leaves next and prev as null (unlinked) until inserted
         * - O(1) field assignment
         */
        private Node(TodoItem task) {
            this.task = task;
        }
    }

    private Node head;
    private Node tail;

    private final Map<Long,Node> nodeByTaskId = new HashMap<>();

    private long nextTaskId = 1;
    private int size = 0;

    /**
     * Adds a task at the end (tail) of the list.
     * - creates a new node for the task (id auto-assigned)
     * - if the list is empty, sets it as both head and tail
     * - otherwise links it after the current tail and updates tail
     * - registers the node in the id-to-node map and increments size
     * - returns the new task's id
     * - O(1) tail insertion (uses the tail pointer directly)
     */
    public long addTask(String text) {
        Node newNode = createNode(text);

        if(head == null) {
            head = newNode;
            tail = newNode;
        } else {
            tail.next = newNode;
            newNode.prev = tail;
            tail = newNode;
        }

        nodeByTaskId.put(newNode.task.getId(), newNode);
        size++;
        return newNode.task.getId();
    }

    /**
     * Adds a task at the beginning (head) of the list.
     * - creates a new node for the task (id auto-assigned)
     * - if the list is empty, sets it as both head and tail
     * - otherwise links it before the current head and updates head
     * - registers the node in the id-to-node map and increments size
     * - returns the new task's id
     * - O(1) head insertion (uses the head pointer directly)
     */
    public long addTaskAtBeginning(String text) {
        Node newNode = createNode(text);
        if(head == null) {
            head = newNode;
            tail = newNode;
        } else {
            newNode.next = head;
            head.prev = newNode;
            head = newNode;
        }

        nodeByTaskId.put(newNode.task.getId(), newNode);
        size++;
        return newNode.task.getId();

    }

    /**
     * Factory helper that builds a node for a new task.
     * - allocates the next sequential task id and advances the counter
     * - trims surrounding whitespace from the text before storing
     * - wraps the new TodoItem in a fresh (unlinked) Node
     * - O(1) allocation
     */
    private Node createNode(String text) {
        TodoItem task = new TodoItem(nextTaskId++,text.trim());
        return new Node(task);
    }

    /**
     * Inserts a new task immediately before an existing task.
     * - looks up the existing node by id in the map
     * - creates a new node and splices it between the existing node and its predecessor
     * - rewires prev/next pointers of the surrounding nodes
     * - if the existing node was the head, the new node becomes the head
     * - registers the new node in the map and returns its id
     * - O(1) splice after the O(1) map lookup (note: size is not incremented here)
     */
    public long insertBefore(long existingTaskId, String text) {
        Node currentNode = nodeByTaskId.get(existingTaskId);
        Node newNode = createNode(text);

        Node previousNode = currentNode.prev;
        newNode.prev = previousNode;
        newNode.next = currentNode;
        currentNode.prev = newNode;

        if(previousNode == null) {
            head = newNode;
        } else {
            previousNode.next = newNode;
        }

        nodeByTaskId.put(newNode.task.getId(), newNode);
        return newNode.task.getId();
    }

    /**
     * Inserts a new task immediately after an existing task.
     * - looks up the existing node by id in the map
     * - creates a new node and splices it between the existing node and its successor
     * - rewires prev/next pointers of the surrounding nodes
     * - if the existing node was the tail, the new node becomes the tail
     * - registers the new node in the map and returns its id
     * - O(1) splice after the O(1) map lookup (note: size is not incremented here)
     */
    public long insertAfter(long existingTaskId, String text) {
        Node currentNode = nodeByTaskId.get(existingTaskId);

        Node newNode = createNode(text);
        Node nextNode = currentNode.next;

        currentNode.next = newNode;
        newNode.prev = currentNode;
        newNode.next = nextNode;
        if(nextNode == null) {
            tail = newNode;
        } else {
            nextNode.prev = newNode;
        }

        nodeByTaskId.put(newNode.task.getId(), newNode);
        return newNode.task.getId();
    }

    /**
     * Updates the text of an existing task.
     * - looks up the node by id in the map
     * - trims surrounding whitespace from the new text
     * - replaces the underlying task's description
     * - O(1) map lookup plus O(1) update
     */
    public void updateTask(long taskId, String newText) {
       Node node =  nodeByTaskId.get(taskId);
       TodoItem todoItem = node.task;
       todoItem.updateText(newText.trim());
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
     * - note: setCompletionStatus currently always sets true regardless of the argument
     * - O(1) map lookup plus O(1) flag update
     */
    public void markIncomplete(long taskId) {
        setCompletionStatus(taskId,false);
    }

    /**
     * Sets the completion status of a task.
     * - looks up the node by id in the map
     * - intends to apply the supplied completed flag to the task
     * - caveat: the implementation hardcodes setCompleted(true), so the
     *   `completed` parameter is currently ignored and the task is always
     *   marked complete
     * - O(1) map lookup plus O(1) flag update
     */
    public void setCompletionStatus(long taskId, boolean completed) {
        Node node =  nodeByTaskId.get(taskId);
        TodoItem todoItem = node.task;
        todoItem.setCompleted(true);
    }

    /**
     * Toggles a task between complete and incomplete.
     * - looks up the node by id in the map
     * - reads the current completion flag and writes its negation
     * - O(1) map lookup plus O(1) flag update
     */
    public void toggleTask(long taskId) {
        Node node = nodeByTaskId.get(taskId);
        node.task.setCompleted(!node.task.isCompleted());
    }

    /**
     * Deletes a task from the list.
     * - looks up the node by id in the map
     * - unlinks the node, repairing the surrounding prev/next pointers
     * - removes the entry from the id-to-node map and decrements size
     * - O(1) unlink after the O(1) map lookup
     */
    public void deleteTask(long taskId) {
        Node deleteNode = nodeByTaskId.get(taskId);
        unlink(deleteNode);
        nodeByTaskId.remove(taskId);
        size--;
    }

    /**
     * Detaches a node from the doubly linked list.
     * - captures the node's previous and next neighbors
     * - if there is no predecessor, promotes the next node to head; else links prev.next to next
     * - if there is no successor, promotes the prev node to tail; else links next.prev to prev
     * - clears the removed node's prev/next pointers to fully detach it
     * - O(1) pointer rewiring
     */
    private void unlink(Node deleteNode) {
        Node prevNode = deleteNode.prev;
        Node nextNode = deleteNode.next;
        if(prevNode == null) {
            head = nextNode;
        }else {
            prevNode.next = nextNode;
        }

        if(nextNode == null) {
            tail = prevNode;
        } else{
            nextNode.prev = prevNode;
        }

        deleteNode.prev = null;
        deleteNode.next = null;
    }

    /**
     * Returns the number of tasks in the list.
     * - returns the maintained size counter
     * - O(1) (no traversal needed)
     */
    public int size() {
        return size;
    }

    /**
     * Builds a printable string of all tasks from head to tail.
     * - starts at the head node and walks next pointers until null
     * - appends each task's toString() followed by " ( id=<id>)"
     * - terminates each line with the platform line separator
     * - returns the accumulated multi-line string
     * - O(n) traversal over all nodes
     */
    public String display() {
        StringBuilder output = new StringBuilder();
        Node current = head;

        while(current != null) {
            output.append(current.task)
                    .append(" ( id=")
                    .append(current.task.getId())
                    .append(")")
                    .append(System.lineSeparator());

            current = current.next;
        }

        return output.toString();
    }

}
