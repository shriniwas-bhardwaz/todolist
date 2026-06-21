package aggregate;

/** One input row: a group key and an integer value. */
public final class Row {
    public final String key;
    public final long value;

    /**
     * Constructs an immutable input row.
     * - Stores the group key used to bucket rows during aggregation.
     * - Stores the integer value to be combined (summed/max/min) per key.
     * - O(1).
     */
    public Row(String key, long value) {
        this.key = key;
        this.value = value;
    }

    /**
     * Renders the row as "(key, value)" for readable debug/demo output.
     * - Used by demo printing; not part of the aggregation logic.
     * - O(1).
     */
    @Override
    public String toString() {
        return "(" + key + ", " + value + ")";
    }
}
