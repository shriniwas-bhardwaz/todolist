package aggregate.fold;

import aggregate.Row;

import java.util.*;

public class Main {

    /**
     * Demo entry point showing the plain-English start/combine/result Aggregator.
     * - Builds a fixed 5-row dataset with repeated keys A and B (and a single C).
     * - Runs SUM, MAX, MIN, COUNT, AVERAGE through the same GroupAggregator.
     * - Only the Aggregator changes between calls; the grouping code is reused.
     */
    public static void main(String[] args) {
        List<Row> rows = List.of(
                new Row("A", 3),
                new Row("B", 5),
                new Row("A", 2),
                new Row("B", 1),
                new Row("C", 4)
        );

        print("SUM",     GroupAggregator.aggregate(rows, Aggregator.sum()));
        print("MAX",     GroupAggregator.aggregate(rows, Aggregator.max()));
        print("MIN",     GroupAggregator.aggregate(rows, Aggregator.min()));
        print("COUNT",   GroupAggregator.aggregate(rows, Aggregator.count()));
        print("AVERAGE", GroupAggregator.aggregate(rows, Aggregator.average()));
    }

    /**
     * Prints one labeled aggregation result.
     * - Writes the label header, then each "(key, value)" pair indented.
     */
    private static void print(String label, Map<String, Long> result) {
        System.out.println("Aggregate by " + label + ":");
        result.forEach((k, v) -> System.out.println("  (" + k + ", " + v + ")"));
    }
}
