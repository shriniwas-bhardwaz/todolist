package aggregate;

import java.util.*;

/**
 * Approach 1 — the core problem: aggregate rows by key, summing the values.
 *
 * One pass over the rows, accumulating into a hash map: O(n) time, O(k) space
 * where n = number of rows and k = number of distinct keys.
 *
 * LinkedHashMap is used only so the output preserves first-seen key order,
 * which makes results easy to eyeball; the problem allows any order.
 */
public final class AggregateBySum {

    /**
     * Aggregates rows by key, summing all values that share the same key.
     * - Iterates every row once, folding into a LinkedHashMap.
     * - Uses Map.merge: first value for a key seeds it, later values add via Long::sum.
     * - LinkedHashMap preserves first-seen key order in the output.
     * - O(n) time (one pass over rows), O(k) space (k distinct keys).
     */
    public static Map<String, Long> aggregate(List<Row> rows) {
        Map<String, Long> sums = new LinkedHashMap<>();
        for (Row row : rows) {
            // getOrDefault avoids a separate "is this key present?" check.
            sums.merge(row.key, row.value, Long::sum);
        }
        return sums;
    }
}
