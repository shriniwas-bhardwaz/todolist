package aggregate.partition;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Hash-partitioned aggregation (hard case of "too big for memory"): even the
 * DISTINCT KEYS do not all fit in memory at once.
 *
 * Strategy:
 *   1. Stream the input and route each row to one of N temp files by
 *      floorMod(key.hashCode(), N). The same key ALWAYS lands in the same file.
 *   2. Aggregate each partition file independently with an in-memory HashMap.
 * Because a key lives entirely in one partition, partitions never share a key,
 * so their results need NO final merge — just concatenate them to the output.
 *
 * Memory is bounded by the largest single partition, not the whole dataset.
 */
public class PartitionedAggregation {

    /**
     * Top-level driver: partition the input, then aggregate each partition.
     * - Creates a temp directory to hold the N partition files.
     * - Phase 1: createPartitionFiles + partitionInput route every row by key hash.
     * - Closes writers so all buffered rows are flushed before reading them back.
     * - Phase 2: aggregatePartitions reduces each file and writes the final output.
     * - finally: always closes writers and deletes temp files, even on error.
     */
    public static void aggregate(
            Path inputFile,
            Path outputFile,
            int numberOfPartitions
    ) throws IOException {

        Path tempDirectory =
                Files.createTempDirectory("aggregation-partitions");

        Path[] partitionFiles = new Path[numberOfPartitions];
        BufferedWriter[] partitionWriters =
                new BufferedWriter[numberOfPartitions];

        try {
            createPartitionFiles(
                    tempDirectory,
                    partitionFiles,
                    partitionWriters
            );

            partitionInput(
                    inputFile,
                    partitionWriters,
                    numberOfPartitions
            );

            closeWriters(partitionWriters);

            aggregatePartitions(partitionFiles, outputFile);

        } finally {
            closeWriters(partitionWriters);
            deleteTemporaryFiles(partitionFiles, tempDirectory);
        }
    }

    /**
     * Creates the N empty partition files and a buffered writer for each.
     * - Names them partition-0.txt .. partition-(N-1).txt inside the temp directory.
     * - Fills the parallel partitionFiles[] and partitionWriters[] arrays by index.
     */
    private static void createPartitionFiles(
            Path tempDirectory,
            Path[] partitionFiles,
            BufferedWriter[] partitionWriters
    ) throws IOException {

        for (int i = 0; i < partitionFiles.length; i++) {
            partitionFiles[i] =
                    tempDirectory.resolve("partition-" + i + ".txt");

            partitionWriters[i] =
                    Files.newBufferedWriter(partitionFiles[i]);
        }
    }

    /**
     * Phase 1: stream the input and route each row to its partition file.
     * - Reads one line at a time (O(1) memory) and skips blanks.
     * - Computes partition = floorMod(key.hashCode(), N); floorMod keeps it non-negative.
     * - Writes "key,value" to that partition's writer, guaranteeing same key -> same file.
     * - O(n) over all rows.
     */
    private static void partitionInput(
            Path inputFile,
            BufferedWriter[] partitionWriters,
            int numberOfPartitions
    ) throws IOException {

        try (BufferedReader reader =
                     Files.newBufferedReader(inputFile)) {

            String line;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                Row row = parseRow(line);

                int partitionNumber = Math.floorMod(
                        row.key.hashCode(),
                        numberOfPartitions
                );

                BufferedWriter writer =
                        partitionWriters[partitionNumber];

                writer.write(row.key);
                writer.write(",");
                writer.write(Long.toString(row.value));
                writer.newLine();
            }
        }
    }

    /**
     * Phase 2: aggregate every partition file and write the combined output.
     * - Processes one partition at a time, so only that partition's keys are in memory.
     * - Appends each partition's "(key,sum)" results straight to the output file.
     * - No cross-partition merge needed (a key never spans partitions).
     */
    private static void aggregatePartitions(
            Path[] partitionFiles,
            Path outputFile
    ) throws IOException {

        try (BufferedWriter outputWriter =
                     Files.newBufferedWriter(outputFile)) {

            for (Path partitionFile : partitionFiles) {

                Map<String, Long> partitionResult =
                        aggregateSinglePartition(partitionFile);

                for (Map.Entry<String, Long> entry
                        : partitionResult.entrySet()) {

                    outputWriter.write(entry.getKey());
                    outputWriter.write(",");
                    outputWriter.write(
                            Long.toString(entry.getValue())
                    );
                    outputWriter.newLine();
                }
            }
        }
    }

    /**
     * Aggregates one partition file into an in-memory map of key -> sum.
     * - Reads the file line by line and merges each value with Long::sum.
     * - Safe in memory because one partition holds only a fraction of all keys.
     * - O(rows in this partition) time, O(keys in this partition) space.
     */
    private static Map<String, Long> aggregateSinglePartition(
            Path partitionFile
    ) throws IOException {

        Map<String, Long> result = new HashMap<>();

        try (BufferedReader reader =
                     Files.newBufferedReader(partitionFile)) {

            String line;

            while ((line = reader.readLine()) != null) {
                Row row = parseRow(line);

                result.merge(
                        row.key,
                        row.value,
                        Long::sum
                );
            }
        }

        return result;
    }

    /**
     * Parses one "key,value" line into a Row.
     * - Splits on the first comma only (limit 2), so the key can't be broken by stray commas.
     * - Trims whitespace; parses the value as a long.
     * - Throws IllegalArgumentException if the line isn't exactly two parts.
     */
    private static Row parseRow(String line) {
        String[] parts = line.split(",", 2);

        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "Invalid row: " + line
            );
        }

        String key = parts[0].trim();
        long value = Long.parseLong(parts[1].trim());

        return new Row(key, value);
    }

    /**
     * Closes all partition writers, ignoring individual close failures.
     * - Called twice (once to flush before reading, once in finally) — safe to repeat.
     * - Null-checks each slot since writers may not all have been created on error paths.
     */
    private static void closeWriters(
            BufferedWriter[] writers
    ) {

        for (BufferedWriter writer : writers) {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Cleans up all temp partition files and the temp directory.
     * - Deletes each partition file (ignoring failures), then the now-empty directory.
     * - Runs in the finally block so temp space is reclaimed even if aggregation throws.
     */
    private static void deleteTemporaryFiles(
            Path[] partitionFiles,
            Path tempDirectory
    ) {

        for (Path partitionFile : partitionFiles) {
            if (partitionFile != null) {
                try {
                    Files.deleteIfExists(partitionFile);
                } catch (IOException ignored) {
                }
            }
        }

        try {
            Files.deleteIfExists(tempDirectory);
        } catch (IOException ignored) {
        }
    }

    /** A parsed input row: a group key and its long value (local to this file). */
    private static class Row {
        private final String key;
        private final long value;

        /**
         * Holds one parsed key/value pair.
         * - Immutable; used only as a parse result inside this class.
         */
        private Row(String key, long value) {
            this.key = key;
            this.value = value;
        }
    }

    /**
     * Demo entry point for partitioned aggregation.
     * - Reads "input.txt", writes aggregated "output.txt", using 10 partitions.
     * - Prints where the result was written.
     */
    public static void main(String[] args) throws IOException {
        Path inputFile = Path.of("input.txt");
        Path outputFile = Path.of("output.txt");

        aggregate(inputFile, outputFile, 10);

        System.out.println(
                "Aggregation written to: " + outputFile
        );
    }
}