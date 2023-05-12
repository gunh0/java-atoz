// Numeric collectors:
//   summingInt/Long/Double(f), averagingInt/Long/Double(f)
//   summarizingInt/Long/Double(f) -> count, sum, min, average, max in ONE pass
//   (IntSummaryStatistics: min/max are MAX_VALUE/MIN_VALUE when empty)
// Collectors.teeing(c1, c2, merger) (Java 12) feeds every element to two
// collectors and merges their results, so two aggregates need one pass:
// e.g. min and max together, or a count of matches and a total.
// All of them work as downstream collectors of groupingBy too.
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class SummarizingExample {
    record Request(String endpoint, int latencyMs, boolean ok) {}

    record Ratio(long failed, long total) {
        @Override
        public String toString() {
            return failed + "/" + total + String.format(" (%.1f%%)", 100.0 * failed / total);
        }
    }

    public static void main(String[] args) {
        List<Request> reqs = List.of(
                new Request("/login", 120, true),
                new Request("/login", 340, false),
                new Request("/search", 80, true),
                new Request("/search", 95, true),
                new Request("/login", 210, true),
                new Request("/export", 1800, true));

        int total = reqs.stream().collect(Collectors.summingInt(Request::latencyMs));
        double avg = reqs.stream().collect(Collectors.averagingInt(Request::latencyMs));
        System.out.printf("sum = %d ms, average = %.2f ms%n", total, avg);

        IntSummaryStatistics stats = reqs.stream().collect(Collectors.summarizingInt(Request::latencyMs));
        System.out.println(stats);
        System.out.println("max - min = " + (stats.getMax() - stats.getMin()) + " ms");

        IntSummaryStatistics empty = List.<Request>of().stream().collect(Collectors.summarizingInt(Request::latencyMs));
        System.out.println("empty: count=" + empty.getCount() + " min=" + empty.getMin()
                + " average=" + empty.getAverage());

        // Per endpoint
        Map<String, IntSummaryStatistics> perEndpoint = reqs.stream()
                .collect(Collectors.groupingBy(Request::endpoint, TreeMap::new,
                        Collectors.summarizingInt(Request::latencyMs)));
        perEndpoint.forEach((e, s) -> System.out.printf("  %-8s n=%d avg=%.1f max=%d%n",
                e, s.getCount(), s.getAverage(), s.getMax()));

        // teeing: error rate in one pass
        Ratio errorRate = reqs.stream().collect(Collectors.teeing(
                Collectors.filtering(r -> !r.ok(), Collectors.counting()),
                Collectors.counting(),
                Ratio::new));
        System.out.println("error rate: " + errorRate);

        // teeing: fastest and slowest endpoint in one pass
        String range = reqs.stream().collect(Collectors.teeing(
                Collectors.minBy(java.util.Comparator.comparingInt(Request::latencyMs)),
                Collectors.maxBy(java.util.Comparator.comparingInt(Request::latencyMs)),
                (min, max) -> min.orElseThrow().endpoint() + " .. " + max.orElseThrow().endpoint()));
        System.out.println("fastest .. slowest: " + range);
    }
}

/* Output:
sum = 2645 ms, average = 440.83 ms
IntSummaryStatistics{count=6, sum=2645, min=80, average=440.833333, max=1800}
max - min = 1720 ms
empty: count=0 min=2147483647 average=0.0
  /export  n=1 avg=1800.0 max=1800
  /login   n=3 avg=223.3 max=340
  /search  n=2 avg=87.5 max=95
error rate: 1/6 (16.7%)
fastest .. slowest: /search .. /export
*/
