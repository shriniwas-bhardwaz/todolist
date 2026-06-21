//package nestedtodo;
//
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//
//public final class TaskRe {
//    private final long id;
//    private String text;
//    private boolean completed;
//
//    /**
//     *
//     *  we need a parent reference if
//     *   - move task to the parent level.
//     *   - find siblings
//     *   - delete a task from its current parent.
//     *   - prevent cycles while moving tasks.
//     *   - outdent a task.
//     */
//    private TaskRe parent;
//    private final List<TaskRe> children;
//
//    public TaskRe(long id, String text, TaskRe parent) {
//        this.id = id;
//        this.text = text;
//        this.parent = parent;
//        this.children = new ArrayList<>();
//    }
//
//    public long getId() {
//        return id;
//    }
//
//    public String getText() {
//        return text;
//    }
//
//    public void updateText(String text) {
//        this.text = text;
//    }
//
//    public boolean isCompleted() {
//        return completed;
//    }
//
//    public void setCompleted(boolean completed) {
//        this.completed = completed;
//    }
//
//    public Long getParentId() {
//        return parent == null ? null : parent.id;
//    }
//
//    public List<TaskRe> getChildren() {
//        return Collections.unmodifiableList(children);
//    }
//
//    public TaskRe getParent() {
//        return parent;
//    }
//
//    public void setParent(TaskRe parent) {
//        this.parent = parent;
//    }
//
//}
