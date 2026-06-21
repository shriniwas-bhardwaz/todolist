package aggregate;

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

        Map<String, Long> result = AggregateBySum.aggregate(rows);

        System.out.println("Aggregate by SUM:");
        result.forEach((key, sum) -> System.out.println("(" + key + ", " + sum + ")"));
    }
}
