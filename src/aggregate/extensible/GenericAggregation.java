package aggregate.extensible;

import aggregate.Row;

import java.util.*;

public class GenericAggregation {

    static class Row {
        private final String key;
        private final int value;

        Row(String key, int value) {
            this.key = key;
            this.value = value;
        }

        public String getKey() {
            return key;
        }

        public int getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "(" + key + ", " + value + ")";
        }
    }

    interface AggregationStrategy {
        int aggregate(int currentValue, int newValue);
    }

    static class SumAggregation implements AggregationStrategy {

        @Override
        public int aggregate(int currentValue, int newValue) {
            return currentValue + newValue;
        }
    }

    static class MaxAggregation implements AggregationStrategy {

        @Override
        public int aggregate(int currentValue, int newValue) {
            return Math.max(currentValue, newValue);
        }
    }

    static class MinAggregation implements AggregationStrategy {

        @Override
        public int aggregate(int currentValue, int newValue) {
            return Math.min(currentValue, newValue);
        }
    }

    public static List<Row> aggregate(
            List<Row> rows,
            AggregationStrategy strategy) {

        Map<String, Integer> aggregated = new HashMap<>();

        for (Row row : rows) {
            String key = row.getKey();
            int value = row.getValue();

            if (!aggregated.containsKey(key)) {
                aggregated.put(key, value);
            } else {
                int currentValue = aggregated.get(key);

                int updatedValue =
                        strategy.aggregate(currentValue, value);

                aggregated.put(key, updatedValue);
            }
        }

        List<Row> result = new ArrayList<>();

        for (Map.Entry<String, Integer> entry
                : aggregated.entrySet()) {

            result.add(
                    new Row(entry.getKey(), entry.getValue())
            );
        }

        return result;
    }

    public static void main(String[] args) {

        List<Row> rows = List.of(
                new Row("A", 3),
                new Row("B", 5),
                new Row("A", 2),
                new Row("B", 1),
                new Row("C", 4)
        );

        List<Row> sumResult =
                aggregate(rows, new SumAggregation());

        List<Row> maxResult =
                aggregate(rows, new MaxAggregation());

        List<Row> minResult = aggregate(rows,new MinAggregation());

        System.out.println("SUM: " + sumResult);
        System.out.println("MAX: " + maxResult);
        System.out.println("MIN: " + minResult);
    }
}