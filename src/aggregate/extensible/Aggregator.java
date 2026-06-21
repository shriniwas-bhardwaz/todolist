package aggregate.extensible;

/**
 * A pluggable aggregation strategy over a stream of long values for one key.
 *
 * The contract mirrors a fold/reduce:
 *   identity()        -> the starting accumulator for a fresh key
 *   accumulate(acc,v) -> fold one new value into the accumulator
 *   finish(acc)       -> turn the accumulator into the reported result
 *
 * Keeping accumulator and result distinct lets aggregations like AVERAGE carry
 * extra state (sum + count) while still reporting a single number.
 *
 * @param <A> the accumulator type
 */
public interface Aggregator<A> {

    A identity();

    A accumulate(A acc, long value);

    long finish(A acc);

    // ---- built-in aggregators -------------------------------------------------

    static Aggregator<Long> sum() {
        return new Aggregator<>() {
            public Long identity()                     { return 0L; }
            public Long accumulate(Long acc, long v)   { return acc + v; }
            public long finish(Long acc)               { return acc; }
        };
    }

    static Aggregator<Long> max() {
        return new Aggregator<>() {
            public Long identity()                     { return Long.MIN_VALUE; }
            public Long accumulate(Long acc, long v)   { return Math.max(acc, v); }
            public long finish(Long acc)               { return acc; }
        };
    }

    static Aggregator<Long> min() {
        return new Aggregator<>() {
            public Long identity()                     { return Long.MAX_VALUE; }
            public Long accumulate(Long acc, long v)   { return Math.min(acc, v); }
            public long finish(Long acc)               { return acc; }
        };
    }

    static Aggregator<Long> count() {
        return new Aggregator<>() {
            public Long identity()                     { return 0L; }
            public Long accumulate(Long acc, long v)   { return acc + 1; }
            public long finish(Long acc)               { return acc; }
        };
    }

    /** AVERAGE (truncated to long): accumulator carries both running sum and count. */
    static Aggregator<long[]> average() {
        return new Aggregator<>() {
            public long[] identity()                       { return new long[]{0L, 0L}; }
            public long[] accumulate(long[] acc, long v)   { acc[0] += v; acc[1]++; return acc; }
            public long finish(long[] acc)                 { return acc[1] == 0 ? 0 : acc[0] / acc[1]; }
        };
    }
}
