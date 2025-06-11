// Collectors.teeing(c1, c2, merger) sends every element to two collectors and
// merges both results, so two aggregates cost one pass over the data. Beyond
// "min and max together", some patterns that come up in real code:
//  - a record as the merger, so the result has names instead of a Pair
//  - teeing as the downstream of groupingBy: two aggregates per group
//  - nesting teeing for three or more aggregates
//  - filtering/mapping downstreams to split input into accepted values and
//    error messages in one pass (validation)
//  - "top N plus the grand total", "count vs distinct count" (duplicates),
//    weighted averages (sum of w*x and sum of w)
// It also works on parallel streams, because each side is an ordinary
// Collector with its own combiner.
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.filtering;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.teeing;
import static java.util.stream.Collectors.toList;

public class TeeingPatternsExample {
    record Request(String endpoint, int status, long millis) {}

    record Stats(long requests, long failures) {}

    record Range(int min, int max, double average) {}

    record Validation(List<Integer> accepted, List<String> errors) {}

    record TopAndTotal(List<String> top, long total) {}

    record Grade(String course, double score, int credits) {}

    public static void main(String[] args) {
        List<Request> log = List.of(
                new Request("/login", 200, 120), new Request("/login", 401, 30),
                new Request("/search", 200, 450), new Request("/search", 200, 980),
                new Request("/search", 500, 2100), new Request("/profile", 200, 80));

        // 1. Per-group pair of aggregates, named with a record
        Map<String, Stats> perEndpoint = log.stream().collect(Collectors.groupingBy(Request::endpoint, TreeMap::new,
                teeing(counting(), filtering(r -> r.status() >= 400, counting()), Stats::new)));
        perEndpoint.forEach((endpoint, stats) -> System.out.println(endpoint + " -> " + stats));

        // 2. Nested teeing: three aggregates in one pass
        List<Integer> latencies = List.of(120, 30, 450, 980, 2100, 80);
        Range range = latencies.stream().collect(teeing(
                Collectors.<Integer>minBy(Comparator.naturalOrder()),
                teeing(Collectors.<Integer>maxBy(Comparator.naturalOrder()), Collectors.averagingInt(Integer::intValue),
                        (max, avg) -> Map.entry(max.orElseThrow(), avg)),
                (min, maxAvg) -> new Range(min.orElseThrow(), maxAvg.getKey(), maxAvg.getValue())));
        System.out.println(range);

        // 3. Validation: accepted values and errors side by side
        List<String> input = List.of("8080", "443", "http", "70000", "22", "-1");
        Validation v = input.stream().collect(teeing(
                filtering(TeeingPatternsExample::isPort, mapping(Integer::parseInt, toList())),
                filtering(s -> !isPort(s), mapping(s -> "not a port: " + s, toList())),
                Validation::new));
        System.out.println("accepted " + v.accepted() + ", errors " + v.errors());

        // 4. Top N and the grand total
        Map<String, Long> bytesByUser = Map.of("ann", 900L, "bob", 4200L, "cyd", 150L, "dee", 3100L, "eve", 2600L);
        TopAndTotal tt = bytesByUser.entrySet().stream().collect(teeing(
                Collectors.collectingAndThen(toList(), all -> all.stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .limit(3).map(e -> e.getKey() + "=" + e.getValue()).toList()),
                Collectors.summingLong(Map.Entry::getValue),
                TopAndTotal::new));
        System.out.println("top 3 " + tt.top() + " of " + tt.total() + " bytes");

        // 5. Duplicates: count vs distinct count
        List<String> invoiceIds = List.of("INV-1", "INV-2", "INV-3", "INV-2", "INV-4", "INV-1");
        String dupes = invoiceIds.stream().collect(teeing(counting(), Collectors.toSet(),
                (n, distinct) -> (n - distinct.size()) + " duplicate(s) among " + n));
        System.out.println(dupes);

        // 6. Weighted average (credits as weights)
        List<Grade> grades = List.of(new Grade("math", 3.7, 4), new Grade("art", 4.0, 1), new Grade("cs", 3.3, 3));
        double gpa = grades.stream().collect(teeing(
                Collectors.summingDouble(g -> g.score() * g.credits()),
                Collectors.summingInt(Grade::credits),
                (weighted, credits) -> weighted / credits));
        System.out.printf("GPA %.3f (unweighted %.3f)%n", gpa, grades.stream().mapToDouble(Grade::score).average().orElseThrow());

        // 7. Same result in parallel
        String evenOdd = IntStream.rangeClosed(1, 100_000).boxed().parallel().collect(teeing(
                filtering(i -> i % 2 == 0, Collectors.summingLong(Integer::longValue)),
                filtering(i -> i % 2 != 0, counting()),
                (evenSum, oddCount) -> "even sum " + evenSum + ", odd count " + oddCount));
        System.out.println("parallel: " + evenOdd);
    }

    static boolean isPort(String s) {
        if (!s.matches("\\d{1,5}")) {
            return false;
        }
        int n = Integer.parseInt(s);
        return n >= 1 && n <= 65535;
    }
}

/* Output:
/login -> Stats[requests=2, failures=1]
/profile -> Stats[requests=1, failures=0]
/search -> Stats[requests=3, failures=1]
Range[min=30, max=2100, average=626.6666666666666]
accepted [8080, 443, 22], errors [not a port: http, not a port: 70000, not a port: -1]
top 3 [bob=4200, dee=3100, eve=2600] of 10950 bytes
2 duplicate(s) among 6
GPA 3.588 (unweighted 3.667)
parallel: even sum 2500050000, odd count 50000
*/
