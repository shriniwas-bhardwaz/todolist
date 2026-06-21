package aggregate.external;

import aggregate.Row;
import aggregate.extensible.Aggregator;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Approach 3b (follow-up #2, hard case) — even the DISTINCT KEYS do not fit in
 * memory, so we cannot keep one accumulator per key.
 *
 * Standard answer: EXTERNAL SORT-MERGE aggregation, the strategy a database uses
 * for a large GROUP BY.
 *
 *   1. Partition the input into chunks that each fit in memory.
 *   2. Sort each chunk by key and write it to a temp "run" (spill to disk).
 *   3. K-way merge the sorted runs. Because the merged stream is ordered by key,
 *      all rows for a key arrive CONSECUTIVELY — so we aggregate one key at a
 *      time and emit it the moment the key changes, holding only ONE accumulator.
 *
 * Memory is O(chunk size + number of runs), independent of total rows or keys.
 *
 * This class simulates the disk runs with in-memory lists so it runs as a plain
 * demo; the algorithm and memory profile are identical to a real spill-to-disk
 * implementation (swap the List<Row> runs for files + buffered readers).
 */
public final class ExternalSortAggregator {

    private final int chunkSize; // max rows held in memory while sorting a run

    public ExternalSortAggregator(int chunkSize) {
        if (chunkSize <= 0) throw new IllegalArgumentException("chunkSize must be > 0");
        this.chunkSize = chunkSize;
    }

    public <A> void aggregate(
            Iterator<Row> rows,
            Aggregator<A> aggregator,
            BiConsumer<String, Long> sink
    ) {
        List<List<Row>> runs = createSortedRuns(rows); // phase 1+2: spill sorted chunks
        mergeAndAggregate(runs, aggregator, sink);     // phase 3: merge + group consecutive
    }

    /** Phase 1+2: read up to chunkSize rows, sort by key, emit as a sorted run. */
    private List<List<Row>> createSortedRuns(Iterator<Row> rows) {
        List<List<Row>> runs = new ArrayList<>();
        List<Row> chunk = new ArrayList<>(chunkSize);

        while (rows.hasNext()) {
            chunk.add(rows.next());
            if (chunk.size() == chunkSize) {
                runs.add(sortRun(chunk));
                chunk = new ArrayList<>(chunkSize);
            }
        }
        if (!chunk.isEmpty()) runs.add(sortRun(chunk));
        return runs;
    }

    private List<Row> sortRun(List<Row> chunk) {
        chunk.sort(Comparator.comparing(r -> r.key));
        return chunk; // in a real system: write to a temp file, return a handle
    }

    /**
     * Phase 3: k-way merge of the sorted runs using a min-heap on the current head
     * of each run. Rows come out globally ordered by key, so we accumulate while
     * the key is unchanged and flush when it advances.
     */
    private <A> void mergeAndAggregate(
            List<List<Row>> runs,
            Aggregator<A> aggregator,
            BiConsumer<String, Long> sink
    ) {
        // Each cursor points into one run. The heap orders cursors by their head key.
        PriorityQueue<Cursor> heap =
                new PriorityQueue<>(Comparator.comparing(c -> c.head().key));
        for (List<Row> run : runs) {
            if (!run.isEmpty()) heap.add(new Cursor(run));
        }

        String currentKey = null;
        A acc = null;

        while (!heap.isEmpty()) {
            Cursor cursor = heap.poll();
            Row row = cursor.head();

            if (currentKey == null || !row.key.equals(currentKey)) {
                if (currentKey != null) sink.accept(currentKey, aggregator.finish(acc));
                currentKey = row.key;
                acc = aggregator.identity();
            }
            acc = aggregator.accumulate(acc, row.value);

            if (cursor.advance()) heap.add(cursor); // re-add if the run has more rows
        }
        if (currentKey != null) sink.accept(currentKey, aggregator.finish(acc));
    }

    /** A position within one sorted run. */
    private static final class Cursor {
        private final List<Row> run;
        private int index;

        Cursor(List<Row> run) { this.run = run; }

        Row head() { return run.get(index); }

        /** Move to the next row; returns false when the run is exhausted. */
        boolean advance() { return ++index < run.size(); }
    }
}
