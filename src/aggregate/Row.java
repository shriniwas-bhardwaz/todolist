package aggregate;

/** One input row: a group key and an integer value. */
public final class Row {
    public final String key;
    public final long value;

    public Row(String key, long value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public String toString() {
        return "(" + key + ", " + value + ")";
    }
}
