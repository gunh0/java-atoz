// Collectors.groupingBy(classifier) is SQL's GROUP BY: it builds a
// Map<K, List<T>> keyed by classifier(element). A second argument, the
// downstream collector, decides what each group becomes instead of a List:
//   counting(), summingInt(..), averagingDouble(..), maxBy(..),
//   mapping(f, toSet()), filtering(p, ..), flatMapping(..), joining(..)
// The three-argument form also picks the map type (TreeMap::new for sorted
// keys). Downstream collectors nest, so groups of groups are one expression.
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class GroupingByExample {
    record Event(String user, String type, String ip, int severity) {}

    public static void main(String[] args) {
        List<Event> events = List.of(
                new Event("alice", "login", "10.0.0.5", 1),
                new Event("bob", "login_failed", "203.0.113.9", 3),
                new Event("bob", "login_failed", "203.0.113.9", 3),
                new Event("bob", "login", "203.0.113.9", 2),
                new Event("carol", "sudo", "10.0.0.8", 4),
                new Event("alice", "sudo", "10.0.0.5", 4),
                new Event("mallory", "login_failed", "198.51.100.7", 3));

        // Plain grouping: type -> list of events
        Map<String, List<Event>> byType = events.stream()
                .collect(Collectors.groupingBy(Event::type, TreeMap::new, Collectors.toList()));
        byType.forEach((type, list) -> System.out.println(type + ": " + list.size() + " events"));

        // Counting per key
        Map<String, Long> perUser = events.stream()
                .collect(Collectors.groupingBy(Event::user, TreeMap::new, Collectors.counting()));
        System.out.println("events per user: " + perUser);

        // mapping: which distinct IPs did each user come from?
        Map<String, Set<String>> ipsPerUser = events.stream()
                .collect(Collectors.groupingBy(Event::user, TreeMap::new,
                        Collectors.mapping(Event::ip, Collectors.toSet())));
        System.out.println("IPs per user: " + ipsPerUser);

        // filtering keeps empty groups (unlike filtering before grouping)
        Map<String, Long> failedPerUser = events.stream()
                .collect(Collectors.groupingBy(Event::user, TreeMap::new,
                        Collectors.filtering(e -> e.type().equals("login_failed"), Collectors.counting())));
        System.out.println("failed logins per user: " + failedPerUser);

        // maxBy and summing
        Map<String, Optional<Event>> worstPerUser = events.stream()
                .collect(Collectors.groupingBy(Event::user, TreeMap::new,
                        Collectors.maxBy(java.util.Comparator.comparingInt(Event::severity))));
        worstPerUser.forEach((u, e) -> System.out.println("  worst for " + u + ": "
                + e.map(x -> x.type() + "/" + x.severity()).orElse("-")));
        Map<String, Integer> riskScore = events.stream()
                .collect(Collectors.groupingBy(Event::ip, TreeMap::new, Collectors.summingInt(Event::severity)));
        System.out.println("risk score per IP: " + riskScore);

        // Nested: user -> type -> count
        Map<String, Map<String, Long>> matrix = events.stream()
                .collect(Collectors.groupingBy(Event::user, TreeMap::new,
                        Collectors.groupingBy(Event::type, TreeMap::new, Collectors.counting())));
        matrix.forEach((u, m) -> System.out.println("  " + u + " " + m));

        // Grouping by a computed key
        Map<String, List<String>> bySeverityBand = events.stream()
                .collect(Collectors.groupingBy(e -> e.severity() >= 3 ? "high" : "low", TreeMap::new,
                        Collectors.mapping(e -> e.user() + ":" + e.type(), Collectors.toList())));
        System.out.println("high: " + bySeverityBand.get("high"));
        System.out.println("low:  " + bySeverityBand.get("low"));
    }
}

/* Output:
login: 2 events
login_failed: 3 events
sudo: 2 events
events per user: {alice=2, bob=3, carol=1, mallory=1}
IPs per user: {alice=[10.0.0.5], bob=[203.0.113.9], carol=[10.0.0.8], mallory=[198.51.100.7]}
failed logins per user: {alice=0, bob=2, carol=0, mallory=1}
  worst for alice: sudo/4
  worst for bob: login_failed/3
  worst for carol: sudo/4
  worst for mallory: login_failed/3
risk score per IP: {10.0.0.5=5, 10.0.0.8=4, 198.51.100.7=3, 203.0.113.9=8}
  alice {login=1, sudo=1}
  bob {login=1, login_failed=2}
  carol {sudo=1}
  mallory {login_failed=1}
high: [bob:login_failed, bob:login_failed, carol:sudo, alice:sudo, mallory:login_failed]
low:  [alice:login, bob:login]
*/
