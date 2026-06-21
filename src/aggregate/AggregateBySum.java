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

    public static Map<String, Long> aggregate(List<Row> rows) {
        Map<String, Long> sums = new LinkedHashMap<>();
        for (Row row : rows) {
            // getOrDefault avoids a separate "is this key present?" check.
            sums.merge(row.key, row.value, Long::sum);
        }
        return sums;
    }
}
