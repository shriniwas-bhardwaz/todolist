package normaltodolistbased;

public final class TodoItem {

    private final long id;
    private String text;
    private boolean completed;

    /**
     * Constructs a new immutable-id todo item.
     * - stores the supplied id as the permanent identifier
     * - stores the supplied text as the task description
     * - initializes the completion flag to false (not done yet)
     * - O(1) field assignment only
     */
    public TodoItem(long id,String text) {
        this.id = id;
        this.text = text;
        this.completed = false;
    }

    /**
     * Returns the unique identifier of this item.
     * - exposes the immutable id field
     * - O(1) simple accessor
     */
    public long getId() {
        return id;
    }

    /**
     * Returns the current description text of this item.
     * - exposes the mutable text field
     * - O(1) simple accessor
     */
    public String getText() {
        return text;
    }

    /**
     * Replaces the item's description text.
     * - overwrites the existing text with the provided value
     * - stores the value as-is (no trimming performed here)
     * - O(1) field mutation
     */
    public void updateText(String text) {
        this.text = text;
    }

    /**
     * Reports whether this item is marked completed.
     * - returns the current value of the completion flag
     * - O(1) simple accessor
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * Sets the completion state of this item.
     * - overwrites the completion flag with the provided boolean
     * - O(1) field mutation
     */
    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    /**
     * Renders a human-readable representation of the item.
     * - prefixes "[x] " when completed, otherwise "[ ] "
     * - appends the item's text after the checkbox prefix
     * - O(1) string concatenation
     */
    @Override
    public String toString() {
        return (completed ? "[x] " : "[ ] ") + text;
    }


}
