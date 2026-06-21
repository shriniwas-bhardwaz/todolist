package aggregate.extensible;

import aggregate.Row;

import java.util.*;

public class Main {

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

    private static void print(String label, Map<String, Long> result) {
        System.out.println("Aggregate by " + label + ":");
        result.forEach((k, v) -> System.out.println("  (" + k + ", " + v + ")"));
    }
}
