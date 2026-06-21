package normaltodo;

public final class TodoItem {

    private final long id;
    private String text;
    private boolean completed;

    public TodoItem(long id,String text) {
        this.id = id;
        this.text = text;
        this.completed = false;
    }

    public long getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public void updateText(String text) {
        this.text = text;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    @Override
    public String toString() {
        return (completed ? "[x] " : "[ ] ") + text;
    }


}
