package aggregate.external;

import aggregate.Row;
import aggregate.extensible.Aggregator;

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

        // 3a: keys fit in memory — one streaming pass, O(k) memory.
        System.out.println("Streaming (keys fit in memory), SUM:");
        StreamingAggregator.aggregate(
                rows.iterator(),
                Aggregator.sum(),
                (k, v) -> System.out.println("  (" + k + ", " + v + ")"));

        // 3b: keys do NOT fit — external sort-merge. chunkSize=2 forces multiple
        //     spilled runs so the k-way merge actually exercises.
        System.out.println("External sort-merge (chunkSize=2), SUM:");
        new ExternalSortAggregator(2).aggregate(
                rows.iterator(),
                Aggregator.sum(),
                (k, v) -> System.out.println("  (" + k + ", " + v + ")"));

        System.out.println("External sort-merge (chunkSize=2), MAX:");
        new ExternalSortAggregator(2).aggregate(
                rows.iterator(),
                Aggregator.max(),
                (k, v) -> System.out.println("  (" + k + ", " + v + ")"));
    }
}
