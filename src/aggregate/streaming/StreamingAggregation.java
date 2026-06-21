package aggregate.streaming;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Streaming aggregation (easy case of "too big for memory"): the ROWS do not fit
 * in memory, but the set of DISTINCT KEYS does.
 *
 * We read the input file one line at a time and keep only one running total per
 * key, so memory is O(k) (k distinct keys) regardless of how many rows there are.
 */
public class StreamingAggregation {

    /**
     * Sums values by key by streaming the file one line at a time.
     * - Opens the file with a BufferedReader; never loads all rows into memory.
     * - Skips blank lines; splits each line into "key,value" (limit 2 so values can't break it).
     * - Map.merge seeds an absent key with the value, else adds via Long::sum.
     * - O(n) time over n rows; O(k) memory for k distinct keys, independent of row count.
     */
    public static Map<String, Long> aggregate(Path inputFile)
            throws IOException {

        Map<String, Long> result = new HashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(inputFile)) {

            String line;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                String[] parts = line.split(",", 2);

                String key = parts[0].trim();
                long value = Long.parseLong(parts[1].trim());

                result.merge(key, value, Long::sum);
            }
        }

        return result;
    }

    /**
     * Demo entry point for streaming aggregation.
     * - Reads "input.txt" from the working directory.
     * - Aggregates by key and prints each "key,value" line to stdout.
     */
    public static void main(String[] args) throws IOException {
        Path inputFile = Path.of("input.txt");

        Map<String, Long> result = aggregate(inputFile);

        result.forEach((key, value) ->
                System.out.println(key + "," + value)
        );
    }
}
