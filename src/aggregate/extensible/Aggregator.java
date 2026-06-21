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

    /**
     * Supplies the starting accumulator for a fresh key.
     * - Called once the first time a key is seen, before any value is folded in.
     * - Must be a neutral element for the operation (e.g. 0 for sum, MIN_VALUE for max).
     * - O(1).
     */
    A identity();

    /**
     * Folds one new value into the running accumulator for a key.
     * - Pure step of the reduce: takes the prior accumulator plus the next value.
     * - May mutate-and-return the accumulator (as average() does) or return a new one.
     * - Called once per row belonging to the key; O(1) per call.
     */
    A accumulate(A acc, long value);

    /**
     * Converts the final accumulator into the single reported result.
     * - Called once per key after all its values have been accumulated.
     * - Lets stateful accumulators (sum+count) collapse to one long (e.g. average).
     * - O(1).
     */
    long finish(A acc);

    // ---- built-in aggregators -------------------------------------------------

    /**
     * Aggregator that computes the SUM of values per key.
     * - identity() = 0; accumulate adds the value; finish returns the running sum.
     * - Accumulator type is Long.
     * - O(1) per row, O(1) state per key.
     */
    static Aggregator<Long> sum() {
        return new Aggregator<>() {
            public Long identity()                     { return 0L; }
            public Long accumulate(Long acc, long v)   {  return acc + v; }
            public long finish(Long acc)               { return acc; }
        };
    }

    /**
     * Aggregator that computes the MAX value per key.
     * - identity() = Long.MIN_VALUE so any real value wins the first comparison.
     * - accumulate keeps the larger of accumulator and value; finish returns it.
     * - O(1) per row, O(1) state per key.
     */
    static Aggregator<Long> max() {
        return new Aggregator<>() {
            public Long identity()                     { return Long.MIN_VALUE; }
            public Long accumulate(Long acc, long v)   { return Math.max(acc, v); }
            public long finish(Long acc)               { return acc; }
        };
    }

    /**
     * Aggregator that computes the MIN value per key.
     * - identity() = Long.MAX_VALUE so any real value wins the first comparison.
     * - accumulate keeps the smaller of accumulator and value; finish returns it.
     * - O(1) per row, O(1) state per key.
     */
    static Aggregator<Long> min() {
        return new Aggregator<>() {
            public Long identity()                     { return Long.MAX_VALUE; }
            public Long accumulate(Long acc, long v)   { return Math.min(acc, v); }
            public long finish(Long acc)               { return acc; }
        };
    }

    /**
     * Aggregator that COUNTS the rows per key (value is ignored).
     * - identity() = 0; accumulate adds 1 per row regardless of value; finish returns the tally.
     * - O(1) per row, O(1) state per key.
     */
    static Aggregator<Long> count() {
        return new Aggregator<>() {
            public Long identity()                     { return 0L; }
            public Long accumulate(Long acc, long v)   { return acc + 1; }
            public long finish(Long acc)               { return acc; }
        };
    }

    /**
     * Aggregator that computes the AVERAGE per key (integer-truncated to long).
     * - Accumulator is a 2-slot long[]: index 0 = running sum, index 1 = running count.
     * - identity() = {0,0}; accumulate adds value to sum and bumps count (mutates in place).
     * - finish returns sum/count, guarding against divide-by-zero by returning 0 for empty.
     * - Shows why accumulator and result types differ: extra state collapses to one long.
     * - O(1) per row, O(1) state per key.
     */
    static Aggregator<long[]> average() {
        return new Aggregator<>() {
            public long[] identity()                       { return new long[]{0L, 0L}; }
            public long[] accumulate(long[] acc, long v)   { acc[0] += v; acc[1]++; return acc; }
            public long finish(long[] acc)                 { return acc[1] == 0 ? 0 : acc[0] / acc[1]; }
        };
    }

}
