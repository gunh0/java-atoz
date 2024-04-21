// JEP 456 — Unnamed Variables & Patterns (Java 22): unnamed variables
//
// Some variables must be declared but are never used: the exception in a
// catch block that only logs a fixed message, the loop variable of a loop
// that just counts, a lambda parameter the callback ignores, the resource
// of a try-with-resources that is only there for its close(). Giving them a
// name like `ignored` invites readers (and linters) to look for a use that
// does not exist. Since Java 22 such a variable can be written as `_`: it
// declares nothing, cannot be read, and several `_` can appear in the same
// scope. (`_` stopped being a legal identifier in Java 9 for this purpose.)
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Collectors;

public class UnnamedVariables {
    public static void main(String[] args) {
        List<String> inputs = List.of("42", "x7", "1000", "");

        // Before: the exception variable needs a name nobody reads
        int validBefore = 0;
        for (String s : inputs) {
            try {
                Integer.parseInt(s);
                validBefore++;
            } catch (NumberFormatException ignored) {
                // just skip it
            }
        }

        // After: `_` says "deliberately unused"
        int valid = 0;
        for (String s : inputs) {
            try {
                Integer.parseInt(s);
                valid++;
            } catch (NumberFormatException _) {
                // just skip it
            }
        }
        System.out.println("valid numbers: before " + validBefore + ", after " + valid);

        // An enhanced for that only counts
        int count = 0;
        for (String _ : inputs) {
            count++;
        }
        System.out.println("inputs counted: " + count);

        // Lambda parameters: the value-mapper ignores the element
        Map<String, Integer> zeroes = inputs.stream()
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toMap(s -> s, _ -> 0, (a, _) -> a, java.util.TreeMap::new));
        System.out.println("initial scores: " + zeroes);

        // BiFunction where only the key matters
        Map<String, Integer> hits = new HashMap<>();
        hits.put("home", 1);
        hits.computeIfPresent("home", (_, n) -> n + 1);
        System.out.println("home hits: " + hits.get("home"));

        // Several `_` in one scope, e.g. draining a queue two at a time
        Queue<Integer> q = new ArrayDeque<>(List.of(1, 2, 3, 4, 5, 6));
        int pairsDropped = 0;
        while (q.size() >= 2) {
            var _ = q.remove();
            var _ = q.remove();
            pairsDropped++;
        }
        System.out.println("pairs dropped: " + pairsDropped + ", left: " + q);

        // A resource used only for its side effect on close()
        try (var _ = new Span("request")) {
            System.out.println("  handling request");
        }
    }

    record Span(String name) implements AutoCloseable {
        Span {
            System.out.println("open span " + name);
        }

        @Override
        public void close() {
            System.out.println("close span " + name);
        }
    }
}

/* Output:
valid numbers: before 2, after 2
inputs counted: 4
initial scores: {1000=0, 42=0, x7=0}
home hits: 2
pairs dropped: 3, left: []
open span request
  handling request
close span request
*/
