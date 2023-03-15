// Intermediate operations that reorder or cut a stream:
//   sorted() / sorted(comparator)   needs every element before emitting one
//   distinct()                      drops duplicates by equals()/hashCode()
//   limit(n) / skip(n)              keep the first n / drop the first n
//   takeWhile(p) / dropWhile(p)     (Java 9) cut at the FIRST element that
//                                   fails p; unlike filter, they stop looking
// limit and takeWhile are short-circuiting: they make infinite streams usable.
// Order matters: limit-then-sort is not sort-then-limit.
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class SlicingExample {
    record Alert(int severity, String text) {}

    public static void main(String[] args) {
        List<String> ips = List.of("10.0.0.9", "10.0.0.2", "10.0.0.9", "10.0.0.4", "10.0.0.2", "10.0.0.1");
        System.out.println("distinct: " + ips.stream().distinct().toList());
        System.out.println("distinct + sorted: " + ips.stream().distinct().sorted().toList());

        // Pagination with skip and limit
        List<Integer> ids = Stream.iterate(1, i -> i + 1).limit(23).toList();
        int pageSize = 10;
        for (int page = 0; page < 3; page++) {
            System.out.println("page " + page + ": " + ids.stream().skip((long) page * pageSize).limit(pageSize).toList());
        }

        // Top 3 by severity, then text: sort first, then limit
        List<Alert> alerts = List.of(
                new Alert(2, "disk 80%"), new Alert(5, "root login"), new Alert(3, "port scan"),
                new Alert(5, "malware hash"), new Alert(1, "cert expires in 60d"));
        List<String> top = alerts.stream()
                .sorted(Comparator.comparingInt(Alert::severity).reversed().thenComparing(Alert::text))
                .limit(3)
                .map(a -> a.severity() + " " + a.text())
                .toList();
        System.out.println("top 3: " + top);
        System.out.println("limit then sort (wrong): "
                + alerts.stream().limit(3).sorted(Comparator.comparingInt(Alert::severity).reversed())
                        .map(Alert::text).toList());

        // takeWhile/dropWhile vs filter on an ordered log: response times in ms
        List<Integer> latency = List.of(40, 55, 61, 900, 45, 1200, 50);
        System.out.println("filter(< 100):    " + latency.stream().filter(ms -> ms < 100).toList());
        System.out.println("takeWhile(< 100): " + latency.stream().takeWhile(ms -> ms < 100).toList());
        System.out.println("dropWhile(< 100): " + latency.stream().dropWhile(ms -> ms < 100).toList());

        // takeWhile ends an infinite stream
        System.out.println("retry delays < 10s: "
                + Stream.iterate(500, d -> d * 2).takeWhile(d -> d < 10_000).toList());
    }
}

/* Output:
distinct: [10.0.0.9, 10.0.0.2, 10.0.0.4, 10.0.0.1]
distinct + sorted: [10.0.0.1, 10.0.0.2, 10.0.0.4, 10.0.0.9]
page 0: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
page 1: [11, 12, 13, 14, 15, 16, 17, 18, 19, 20]
page 2: [21, 22, 23]
top 3: [5 malware hash, 5 root login, 3 port scan]
limit then sort (wrong): [root login, port scan, disk 80%]
filter(< 100):    [40, 55, 61, 45, 50]
takeWhile(< 100): [40, 55, 61]
dropWhile(< 100): [900, 45, 1200, 50]
retry delays < 10s: [500, 1000, 2000, 4000, 8000]
*/
