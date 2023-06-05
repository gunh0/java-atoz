// A Collector<T, A, R> is four functions plus some flags:
//   supplier()     creates a fresh mutable accumulation container A
//   accumulator()  folds one T into an A
//   combiner()     merges two As (used when a parallel stream splits work)
//   finisher()     turns the final A into the result R
// Collector.of(supplier, accumulator, combiner[, finisher], characteristics)
// builds one without writing a class. Write one when the same aggregation
// shows up in many pipelines and no combination of Collectors fits.
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CustomCollectorExample {

    // Top N by a score, keeping only N elements in memory (a min-heap)
    static Collector<Map.Entry<String, Integer>, ?, List<String>> topN(int n) {
        return Collector.of(
                () -> new PriorityQueue<Map.Entry<String, Integer>>(Map.Entry.comparingByValue()),
                (heap, e) -> {
                    heap.add(e);
                    if (heap.size() > n) heap.poll(); // drop the smallest
                },
                (left, right) -> {
                    right.forEach(left::add);
                    while (left.size() > n) left.poll();
                    return left;
                },
                heap -> {
                    List<Map.Entry<String, Integer>> list = new ArrayList<>(heap);
                    list.sort(Map.Entry.<String, Integer>comparingByValue().reversed()
                            .thenComparing(Map.Entry.comparingByKey()));
                    return list.stream().map(e -> e.getKey() + "=" + e.getValue()).toList();
                });
    }

    // A collector without a finisher: the container itself is the result
    static final class Report {
        private final StringBuilder sb = new StringBuilder();
        private int lines;

        void add(String s) {
            sb.append(lines++ == 0 ? "" : "\n").append("  | ").append(s);
        }

        Report merge(Report other) {
            if (other.lines > 0) {
                if (lines > 0) sb.append("\n");
                sb.append(other.sb);
                lines += other.lines;
            }
            return this;
        }

        @Override
        public String toString() {
            return "report (" + lines + " lines)\n" + sb;
        }
    }

    public static void main(String[] args) {
        Map<String, Integer> hitsPerIp = new LinkedHashMap<>();
        hitsPerIp.put("10.0.0.5", 14);
        hitsPerIp.put("203.0.113.9", 950);
        hitsPerIp.put("198.51.100.7", 310);
        hitsPerIp.put("10.0.0.8", 2);
        hitsPerIp.put("192.0.2.44", 950);

        System.out.println("top 3 talkers: " + hitsPerIp.entrySet().stream().collect(topN(3)));
        // The combiner makes it correct on a parallel stream as well
        System.out.println("top 3 (parallel): " + hitsPerIp.entrySet().parallelStream().collect(topN(3)));

        Collector<String, Report, Report> toReport = Collector.of(Report::new, Report::add, Report::merge);
        Report report = hitsPerIp.keySet().stream().filter(ip -> !ip.startsWith("10.")).collect(toReport);
        System.out.println(report);

        // For comparison: what the built-in collectors give with more ceremony
        String builtIn = hitsPerIp.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(3)
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", ", "[", "]"));
        System.out.println("sorted + limit: " + builtIn + " (sorts everything first)");

        // Large parallel input: the collector still agrees with the sequential answer
        Map<String, Integer> many = new LinkedHashMap<>();
        IntStream.range(0, 10_000).forEach(i -> many.put("host" + i, (i * 37) % 10_007));
        System.out.println("sequential == parallel on 10k entries: "
                + many.entrySet().stream().collect(topN(5)).equals(many.entrySet().parallelStream().collect(topN(5))));
    }
}

/* Output:
top 3 talkers: [192.0.2.44=950, 203.0.113.9=950, 198.51.100.7=310]
top 3 (parallel): [192.0.2.44=950, 203.0.113.9=950, 198.51.100.7=310]
report (3 lines)
  | 203.0.113.9
  | 198.51.100.7
  | 192.0.2.44
sorted + limit: [192.0.2.44=950, 203.0.113.9=950, 198.51.100.7=310] (sorts everything first)
sequential == parallel on 10k entries: true
*/
