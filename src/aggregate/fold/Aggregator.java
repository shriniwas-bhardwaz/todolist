package aggregate.fold;

/**
 * A pluggable aggregation strategy — the same three-step "fold" as the extensible
 * version, just with plain-English method names so it reads without functional
 * programming jargon.
 *
 * Think of aggregating one key's values as filling a bucket:
 *
 *   start()              -> grab an empty bucket           (was: identity)
 *   combine(bucket, v)   -> drop one value into the bucket (was: accumulate)
 *   result(bucket)       -> read the final number off it   (was: finish)
 *
 * Why three methods instead of one? Because the bucket type can differ from the
 * answer type. SUM/MAX keep a single number, but AVERAGE must keep BOTH a running
 * sum and a count in the bucket, then divide at the end — result() is where that
 * extra state collapses into one reported number.
 *
 * @param <B> the bucket (accumulator) type
 */
public interface Aggregator<B> {

    /**
     * Returns a fresh, empty bucket for a key seen for the first time.
     * - Called once per key, before any value goes in.
     * - Should be the neutral starting point (0 for sum, MIN_VALUE for max).
     * - O(1).
     */
    B start();

    /**
     * Drops one value into the bucket and returns the updated bucket.
     * - Called once for every row that belongs to the key.
     * - May update the bucket in place (like average) or return a new one.
     * - O(1) per call.
     */
    B combine(B bucket, long value);

    /**
     * Reads the final answer out of the bucket once all values are in.
     * - Called once per key, after the last value has been combined.
     * - This is where a multi-field bucket (sum+count) becomes a single number.
     * - O(1).
     */
    long result(B bucket);

    // ---- built-in aggregators -------------------------------------------------

    /**
     * SUM of all values for a key.
     * - Empty bucket = 0; each value is added; the answer is the running total.
     */
    static Aggregator<Long> sum() {
        return new Aggregator<>() {
            public Long start()                       { return 0L; }
            public Long combine(Long bucket, long v)  { return bucket + v; }
            public long result(Long bucket)           { return bucket; }
        };
    }

    /**
     * MAX value for a key.
     * - Empty bucket = Long.MIN_VALUE so the first real value always wins.
     * - Each step keeps the larger of bucket and value.
     */
    static Aggregator<Long> max() {
        return new Aggregator<>() {
            public Long start()                       { return Long.MIN_VALUE; }
            public Long combine(Long bucket, long v)  { return Math.max(bucket, v); }
            public long result(Long bucket)           { return bucket; }
        };
    }

    /**
     * MIN value for a key.
     * - Empty bucket = Long.MAX_VALUE so the first real value always wins.
     * - Each step keeps the smaller of bucket and value.
     */
    static Aggregator<Long> min() {
        return new Aggregator<>() {
            public Long start()                       { return Long.MAX_VALUE; }
            public Long combine(Long bucket, long v)  { return Math.min(bucket, v); }
            public long result(Long bucket)           { return bucket; }
        };
    }

    /**
     * COUNT of rows for a key (the value itself is ignored).
     * - Empty bucket = 0; each row adds 1; the answer is the tally.
     */
    static Aggregator<Long> count() {
        return new Aggregator<>() {
            public Long start()                       { return 0L; }
            public Long combine(Long bucket, long v)  { return bucket + 1; }
            public long result(Long bucket)           { return bucket; }
        };
    }

    /**
     * AVERAGE per key (integer-truncated).
     * - The bucket is a 2-slot array: [runningSum, runningCount].
     * - combine adds the value to the sum and bumps the count.
     * - result divides sum by count (returns 0 for an empty bucket to avoid /0).
     * - This is the case that justifies separate bucket and answer types.
     */
    static Aggregator<long[]> average() {
        return new Aggregator<>() {
            public long[] start()                        { return new long[]{0L, 0L}; }
            public long[] combine(long[] bucket, long v) { bucket[0] += v; bucket[1]++; return bucket; }
            public long result(long[] bucket)            { return bucket[1] == 0 ? 0 : bucket[0] / bucket[1]; }
        };
    }
}
