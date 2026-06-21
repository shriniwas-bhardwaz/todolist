package aggregate.simple;

/**
 * How to combine two values for the same key into one.
 *
 * That's the whole abstraction. SUM, MAX, MIN are all just "given the running
 * result and the next value, produce the new running result":
 *
 *   SUM -> a + b
 *   MAX -> max(a, b)
 *   MIN -> min(a, b)
 *
 * No identity / no finish step is needed: the FIRST value for a key seeds the
 * result, and every later value is folded in with combine(). The grouping engine
 * (GroupAggregator) handles that seeding via Map.merge, so this interface stays a
 * single, obvious method — easy to write and reason about in an interview.
 *
 * It is intentionally identical in shape to java.util.function.LongBinaryOperator,
 * so you could even pass Long::sum / Math::max directly.
 */
@FunctionalInterface
public interface Aggregator {

    /**
     * Folds the next value into the running result for a single key.
     * - Sole abstract method; defines the aggregation strategy (SUM/MAX/MIN/custom).
     * - runningResult: accumulated result so far for this key.
     * - nextValue: the new row's value to combine in.
     * - Returns the updated running result; no identity/finish step needed.
     * - O(1) per call.
     */
    long combine(long runningResult, long nextValue);

    // Reusable built-ins — each is a one-liner, which is the point.
    Aggregator SUM = (a, b) -> a + b;
    Aggregator MAX = Math::max;
    Aggregator MIN = Math::min;

}
