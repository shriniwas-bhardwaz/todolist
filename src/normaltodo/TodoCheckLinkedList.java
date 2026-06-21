package normaltodo;

import java.util.HashMap;
import java.util.Map;

public final class TodoCheckLinkedList {

    private static final class Node {

        private final TodoItem task;
        private Node next;
        private Node prev;

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
     *  Adds a task at the end
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
     *  Adds a task at the beginning
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

    private Node createNode(String text) {
        TodoItem task = new TodoItem(nextTaskId++,text.trim());
        return new Node(task);
    }

    /**
     *  Inserts a new task immediately before an existing task
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
     *  Inserts a new task immediately after an existing task
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

    public void updateTask(long taskId, String newText) {
       Node node =  nodeByTaskId.get(taskId);
       TodoItem todoItem = node.task;
       todoItem.updateText(newText.trim());
    }

    public void markComplete(long taskId) {
        setCompletionStatus(taskId,true);
    }

    public void markIncomplete(long taskId) {
        setCompletionStatus(taskId,false);
    }

    public void setCompletionStatus(long taskId, boolean completed) {
        Node node =  nodeByTaskId.get(taskId);
        TodoItem todoItem = node.task;
        todoItem.setCompleted(true);
    }

    public void toggleTask(long taskId) {
        Node node = nodeByTaskId.get(taskId);
        node.task.setCompleted(!node.task.isCompleted());
    }

    public void deleteTask(long taskId) {
        Node deleteNode = nodeByTaskId.get(taskId);
        unlink(deleteNode);
        nodeByTaskId.remove(taskId);
        size--;
    }

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

    public int size() {
        return size;
    }

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
