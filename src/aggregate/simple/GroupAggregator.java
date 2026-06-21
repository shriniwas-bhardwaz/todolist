package aggregate.simple;

import aggregate.Row;

import java.util.*;

/**
 * Aggregate rows by key using a pluggable combine function.
 *
 * SOLID at work:
 *   - Single Responsibility: this class only does the grouping/iteration.
 *     "How to combine values" lives in Aggregator.
 *   - Open/Closed: a new aggregation (e.g. a custom one) is a new Aggregator
 *     lambda; this class never changes.
 *   - Dependency Inversion: it depends on the Aggregator abstraction, not on
 *     any concrete SUM/MAX implementation.
 *
 * O(n) time, O(k) space (n rows, k distinct keys).
 */
public final class GroupAggregator {

    /**
     * Groups rows by key and reduces each group with the supplied Aggregator.
     * - Iterates every row once, folding into a LinkedHashMap.
     * - Map.merge seeds an absent key with row.value, else calls aggregator.combine.
     * - Combine strategy (SUM/MAX/MIN/custom) is injected, so this class never changes.
     * - LinkedHashMap preserves first-seen key order in the output.
     * - O(n) time (one pass over rows), O(k) space (k distinct keys).
     */
    public static Map<String, Long> aggregate(List<Row> rows, Aggregator aggregator) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Row row : rows) {
            // merge: if key is absent, store row.value (seeds the group);
            //        otherwise combine the existing result with the new value.
            result.merge(row.key, row.value, aggregator::combine);
        }
        return result;
    }
}
