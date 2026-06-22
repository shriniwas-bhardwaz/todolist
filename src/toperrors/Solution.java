package toperrors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Top-K error reporter: given a JSON array of request records, find the most
 * frequent error messages within a time window.
 *
 * The JSON is parsed by hand (no library) via simple key-marker string scans,
 * which is why each record's fields are located by their "key": prefix.
 */
public class Solution {

    /**
     * Returns the top-k errors (by frequency) seen within [startTime, endTime).
     * - Step 1: guard null input; strip the outer [ ] and bail on an empty array.
     * - Step 2: split the array into per-record chunks on the "},{" boundary.
     * - Step 3: for each chunk, parse timestamp/status/error, then apply three filters:
     *     in the half-open time window, status >= 400, and error present — counting survivors.
     * - Step 4: sort entries by count descending, breaking ties by error string ascending.
     * - Step 5: take the first k and shape each as a [error, count] row.
     * - O(n) parse/filter + O(d log d) sort, where n = records, d = distinct errors.
     */
    public List<List<Object>> solution(
            String recordsJson, String startTime, String endTime, int k) {

        List<List<Object>> result = new ArrayList<>();

        // --- Step 1: guard empty / null input ---
        if (recordsJson == null) {
            return result;
        }
        String trimmed = recordsJson.trim();

        trimmed = trimmed.substring(1, trimmed.length() - 1);

        if (trimmed.isEmpty()) {
            return result; // zero records -> empty answer
        }

        // --- Step 2: split into individual record chunks ---
        // Records are joined by "},{". Splitting drops the braces at the
        // boundaries, but we don't need them -- we locate fields by their keys.
        String[] chunks = trimmed.split("\\},\\{");

        // --- Step 3: parse + filter + count ---
        Map<String, Integer> counts = new HashMap<>();
        for (String chunk : chunks) {
            String timestamp = extractString(chunk, "timestamp");
            Integer status = extractInt(chunk, "status");
            String error = extractError(chunk);

            // A malformed chunk missing required numeric/timestamp -> skip defensively.
            if (timestamp == null || status == null) {
                continue;
            }
            // Filter 1: half-open time window  startTime <= ts < endTime.
            // Timestamps are normalized ISO-8601 UTC, so string compare == time compare.
            if (timestamp.compareTo(startTime) < 0 || timestamp.compareTo(endTime) >= 0) {
                continue;
            }
            // Filter 2: only error responses.
            if (status < 400) {
                continue;
            }
            // Filter 3: error must exist and be non-null.
            if (error == null) {
                continue;
            }
            counts.merge(error, 1, Integer::sum);
        }

        // --- Step 4: sort (count desc, then error asc) ---
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> {
            if (!a.getValue().equals(b.getValue())) {
                return b.getValue() - a.getValue();    // higher count first
            }
            return a.getKey().compareTo(b.getKey());   // tie -> lexicographic ascending
        });

        // --- Step 5: take top k and shape each row as [error, count] ---
        int limit = Math.min(k, sorted.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Integer> e = sorted.get(i);
            List<Object> pair = new ArrayList<>();
            pair.add(e.getKey());
            pair.add(e.getValue());
            result.add(pair);
        }
        return result;
    }

    /**
     * Extracts a string-valued field: "key":"value" -> value (quotes stripped).
     * - Builds the marker "key":" and finds where the value starts after it.
     * - Returns null if the key is absent or the closing quote is missing.
     * - Reads up to the next double-quote, so commas inside the value are safe.
     */
    private String extractString(String chunk, String key) {
        String marker = "\"" + key + "\":\"";
        int start = chunk.indexOf(marker);
        if (start == -1) {
            return null;
        }
        start += marker.length();                 // first char inside the quotes
        int end = chunk.indexOf('"', start);       // closing quote (commas inside are fine)
        if (end == -1) {
            return null;
        }
        return chunk.substring(start, end);
    }

    /**
     * Extracts a numeric field: "key":123 -> 123.
     * - Finds the marker "key": and scans forward over digits (and a leading '-').
     * - Returns null if the key is absent or no digits follow the colon.
     * - Parses the captured substring as an int.
     */
    private Integer extractInt(String chunk, String key) {
        String marker = "\"" + key + "\":";
        int start = chunk.indexOf(marker);
        if (start == -1) {
            return null;
        }
        start += marker.length();
        int end = start;
        while (end < chunk.length()
                && (Character.isDigit(chunk.charAt(end)) || chunk.charAt(end) == '-')) {
            end++;
        }
        if (end == start) {
            return null;
        }
        return Integer.parseInt(chunk.substring(start, end));
    }

    /**
     * Extracts the error field, which is EITHER "error":"some string" OR "error":null.
     * - Needs special handling (vs extractString) because the value may be the literal null.
     * - Finds the marker "error": and skips any spaces after the colon.
     * - If the value starts with a quote, returns the quoted string (quotes stripped).
     * - Returns null when the field is missing, explicitly null, or non-string.
     */
    private String extractError(String chunk) {
        String marker = "\"error\":";
        int start = chunk.indexOf(marker);
        if (start == -1) {
            return null; // field missing -> treated as null
        }
        start += marker.length();
        // skip any whitespace after the colon
        while (start < chunk.length() && chunk.charAt(start) == ' ') {
            start++;
        }
        if (start >= chunk.length()) {
            return null;
        }
        // String value: begins with a quote.
        if (chunk.charAt(start) == '"') {
            int valStart = start + 1;
            int valEnd = chunk.indexOf('"', valStart);
            if (valEnd == -1) {
                return null;
            }
            return chunk.substring(valStart, valEnd);
        }
        // Otherwise it's the literal null (or anything non-string) -> ignore.
        return null;
    }
}
