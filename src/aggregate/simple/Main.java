package aggregate.simple;

import aggregate.Row;

import java.util.*;

public class Main {

    /**
     * Demo entry point showing the pluggable-aggregator approach.
     * - Builds a sample row list with repeated keys A, B and a single C.
     * - SUM call: sums per key (A=5, B=6, C=4).
     * - MAX call: keeps the largest value per key (A=3, B=5, C=4).
     * - MIN call: keeps the smallest value per key (A=2, B=1, C=4).
     * - LAST call: passes an inline lambda (running, next) -> next, demonstrating
     *   Open/Closed — a brand-new aggregation without changing any class.
     */
    public static void main(String[] args) {
        List<Row> rows = List.of(
                new Row("A", 3),
                new Row("B", 5),
                new Row("A", 2),
                new Row("B", 1),
                new Row("C", 4)
        );

        print("SUM", GroupAggregator.aggregate(rows, Aggregator.SUM));
        print("MAX", GroupAggregator.aggregate(rows, Aggregator.MAX));
        print("MIN", GroupAggregator.aggregate(rows, Aggregator.MIN));


        // Open/Closed in action: a brand-new aggregation without touching any class.
        print("LAST", GroupAggregator.aggregate(rows, (running, next) -> next));
    }

    /**
     * Pretty-prints one labeled aggregation result.
     * - Prints a header line "Aggregate by <label>:".
     * - Iterates the result map, printing each indented "(key, value)" pair.
     * - O(k) over the k entries in the result map.
     */
    private static void print(String label, Map<String, Long> result) {
        System.out.println("Aggregate by " + label + ":");
        result.forEach((k, v) -> System.out.println("  (" + k + ", " + v + ")"));
    }
}
