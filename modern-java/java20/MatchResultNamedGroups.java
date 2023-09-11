// Java 20 — named groups on MatchResult: group(String), start/end(String),
// namedGroups(), hasMatch() (API additions, no JEP)
//
// Named capturing groups, (?<user>...), have existed since Java 7, but
// only Matcher could be asked for them by name. MatchResult, the immutable
// snapshot returned by Matcher.toMatchResult() and streamed by
// Matcher.results() and Scanner.findAll(), knew only numeric indexes. So
// stream-based parsing fell back to group(1), group(2) ... and broke
// silently when someone added a group to the pattern. Java 20 adds the
// by-name accessors to MatchResult, namedGroups() to list the names, and
// hasMatch() to ask whether a match is available without catching
// IllegalStateException.
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MatchResultNamedGroups {
    static final String LOG = """
            2023-09-11T08:01:02 FAIL user=root ip=203.0.113.9
            2023-09-11T08:01:05 OK   user=alice ip=198.51.100.7
            2023-09-11T08:01:09 FAIL user=admin ip=203.0.113.9
            """;

    public static void main(String[] args) {
        Pattern line = Pattern.compile("(?<status>FAIL|OK)\\s+user=(?<user>\\w+) ip=(?<ip>[\\d.]+)");

        // Before: in a stream of MatchResult only numeric groups were available
        List<String> byIndex = line.matcher(LOG).results()
                .filter(r -> r.group(1).equals("FAIL"))
                .map(r -> r.group(2) + "@" + r.group(3)) // which number was the ip again?
                .collect(Collectors.toList());
        System.out.println("before (by index): " + byIndex);

        // After: names work on MatchResult too
        List<String> byName = line.matcher(LOG).results()
                .filter(r -> r.group("status").equals("FAIL"))
                .map(r -> r.group("user") + "@" + r.group("ip"))
                .toList();
        System.out.println("after  (by name):  " + byName);

        // The group names and their indexes, e.g. to build a generic record mapper
        Matcher m = line.matcher(LOG);
        System.out.println("namedGroups: " + new TreeMap<>(m.namedGroups()));

        // A snapshot keeps working after the matcher moves on
        m.find();
        MatchResult first = m.toMatchResult();
        m.find();
        System.out.println("snapshot user=" + first.group("user") + " at [" + first.start("user") + ","
                + first.end("user") + "), matcher now at user=" + m.group("user"));

        // hasMatch(): no more IllegalStateException probing
        Matcher fresh = line.matcher(LOG);
        System.out.println("hasMatch before find: " + fresh.hasMatch());
        fresh.find();
        System.out.println("hasMatch after find:  " + fresh.hasMatch());

        // Generic: turn every match into a map of name -> value
        List<Map<String, String>> rows = line.matcher(LOG).results()
                .map(r -> r.namedGroups().keySet().stream()
                        .collect(Collectors.toMap(k -> k, r::group, (a, b) -> a, TreeMap::new)))
                .collect(Collectors.toList());
        rows.forEach(row -> System.out.println("row: " + row));
    }
}

/* Output:
before (by index): [root@203.0.113.9, admin@203.0.113.9]
after  (by name):  [root@203.0.113.9, admin@203.0.113.9]
namedGroups: {ip=3, status=1, user=2}
snapshot user=root at [30,34), matcher now at user=alice
hasMatch before find: false
hasMatch after find:  true
row: {ip=203.0.113.9, status=FAIL, user=root}
row: {ip=198.51.100.7, status=OK, user=alice}
row: {ip=203.0.113.9, status=FAIL, user=admin}
*/
