package aggregate.fold;

import aggregate.Row;

import java.util.*;

/**
 * Groups rows by key and reduces each group with a pluggable Aggregator.
 *
 * The grouping/iteration is written once here; SUM vs MAX vs AVERAGE differ only
 * in the Aggregator passed in, so adding a new aggregation never touches this class.
 *
 * O(n) time, O(k) space (n rows, k distinct keys).
 */
public final class GroupAggregator {

    /**
     * Aggregates rows by key in a single pass using the supplied Aggregator.
     * - For each row: find the key's bucket, creating an empty one via start() on first sight.
     * - combine() the row's value into the bucket and store it back.
     * - After the pass, result() each bucket into its final long answer.
     * - LinkedHashMap preserves first-seen key order in the output.
     * - O(n) time over n rows, O(k) space for k distinct keys.
     */
    public static <B> Map<String, Long> aggregate(List<Row> rows, Aggregator<B> aggregator) {
        Map<String, B> buckets = new LinkedHashMap<>();

        for (Row row : rows) {
            B bucket = buckets.get(row.key);
            if (bucket == null) {
                bucket = aggregator.start();   // first time we see this key
            }
            buckets.put(row.key, aggregator.combine(bucket, row.value));
        }

        // Read the final answer out of every bucket.
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<String, B> e : buckets.entrySet()) {
            result.put(e.getKey(), aggregator.result(e.getValue()));
        }
        return result;
    }
}
