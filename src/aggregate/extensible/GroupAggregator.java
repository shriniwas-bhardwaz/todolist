package aggregate.extensible;

import aggregate.Row;

import java.util.*;

/**
 * Approach 2 (follow-up #1) — aggregate by key with a PLUGGABLE function.
 *
 * The grouping logic ("one accumulator per key, one pass over rows") is written
 * once. SUM vs MAX vs MIN vs AVERAGE differ only in the Aggregator passed in, so
 * adding a new aggregation never touches this class — it is closed for
 * modification, open for extension.
 *
 * O(n) time, O(k) space, identical to the simple version.
 */
public final class GroupAggregator {

    public static <A> Map<String, Long> aggregate(List<Row> rows, Aggregator<A> aggregator) {
        Map<String, A> accumulators = new LinkedHashMap<>();

        for (Row row : rows) {
            A acc = accumulators.get(row.key);
            if (acc == null) {
                acc = aggregator.identity();
            }
            accumulators.put(row.key, aggregator.accumulate(acc, row.value));
        }

        // Fold each accumulator into its reported result.
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<String, A> e : accumulators.entrySet()) {
            result.put(e.getKey(), aggregator.finish(e.getValue()));
        }
        return result;
    }
}
