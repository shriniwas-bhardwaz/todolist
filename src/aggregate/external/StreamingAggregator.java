package aggregate.external;

import aggregate.Row;
import aggregate.extensible.Aggregator;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Approach 3a (follow-up #2, easy case) — the ROW DATA is too big for memory,
 * but the set of DISTINCT KEYS still fits.
 *
 * We never hold the rows; we consume them from an Iterator (which could be
 * backed by a file/socket/DB cursor reading one row at a time) and keep only
 * one accumulator per key. Memory is O(k), independent of the number of rows.
 *
 * This is exactly how a streaming "group by" works when cardinality is bounded.
 */
public final class StreamingAggregator {

    public static <A> void aggregate(
            Iterator<Row> rows,
            Aggregator<A> aggregator,
            BiConsumer<String, Long> sink   // called once per distinct key at the end
    ) {
        Map<String, A> accumulators = new HashMap<>();

        while (rows.hasNext()) {
            Row row = rows.next();
            A acc = accumulators.get(row.key);
            if (acc == null) acc = aggregator.identity();
            accumulators.put(row.key, aggregator.accumulate(acc, row.value));
        }

        for (Map.Entry<String, A> e : accumulators.entrySet()) {
            sink.accept(e.getKey(), aggregator.finish(e.getValue()));
        }
    }
}
