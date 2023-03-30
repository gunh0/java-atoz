// collect(collector) is the mutable counterpart of reduce: a Collector says
// how to create a container, add an element to it and merge two containers.
// java.util.stream.Collectors has the common ones:
//   toList() / toSet()                  no guarantee about mutability or type
//   toUnmodifiableList() / ...Set()     immutable, rejects nulls
//   toCollection(TreeSet::new)          choose the exact collection
//   joining(sep[, prefix, suffix])      concatenate CharSequences
// Stream.toList() (Java 16) is a shortcut returning an unmodifiable list that,
// unlike toUnmodifiableList(), allows null elements.
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class CollectorsExample {
    public static void main(String[] args) {
        List<String> scanned = List.of("ssh", "http", "https", "ssh", "mysql", "http");

        List<String> asList = scanned.stream().collect(Collectors.toList());
        asList.add("redis"); // currently an ArrayList, but the spec does not promise it
        System.out.println("toList(): " + asList);

        Set<String> asSet = scanned.stream().collect(Collectors.toSet());
        System.out.println("toSet() size: " + asSet.size() + " (order unspecified)");

        TreeSet<String> sorted = scanned.stream().collect(Collectors.toCollection(TreeSet::new));
        System.out.println("toCollection(TreeSet::new): " + sorted + ", first = " + sorted.first());

        ArrayDeque<String> stack = scanned.stream().distinct()
                .collect(Collectors.toCollection(ArrayDeque::new));
        System.out.println("toCollection(ArrayDeque::new) peekLast: " + stack.peekLast());

        // Unmodifiable results
        List<String> frozen = scanned.stream().distinct().collect(Collectors.toUnmodifiableList());
        try {
            frozen.add("ftp");
        } catch (UnsupportedOperationException e) {
            System.out.println("toUnmodifiableList().add -> UnsupportedOperationException");
        }
        List<String> withNull = Arrays.asList("a", null);
        System.out.println("Stream.toList() keeps null: " + withNull.stream().toList());
        try {
            withNull.stream().collect(Collectors.toUnmodifiableList());
        } catch (NullPointerException e) {
            System.out.println("toUnmodifiableList() with null -> NullPointerException");
        }

        // joining
        List<String> services = new ArrayList<>(sorted);
        System.out.println("joining(): " + services.stream().collect(Collectors.joining()));
        System.out.println("joining(\", \"): " + services.stream().collect(Collectors.joining(", ")));
        System.out.println("joining as JSON array: "
                + services.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(",", "[", "]")));
        System.out.println("joining on empty: '"
                + List.<String>of().stream().collect(Collectors.joining(",", "[", "]")) + "'");

        // A firewall rule line built from parts
        String rule = scanned.stream().distinct().filter(s -> !s.equals("mysql"))
                .collect(Collectors.joining(" ", "allow ", " from 10.0.0.0/8"));
        System.out.println(rule);
    }
}

/* Output:
toList(): [ssh, http, https, ssh, mysql, http, redis]
toSet() size: 4 (order unspecified)
toCollection(TreeSet::new): [http, https, mysql, ssh], first = http
toCollection(ArrayDeque::new) peekLast: mysql
toUnmodifiableList().add -> UnsupportedOperationException
Stream.toList() keeps null: [a, null]
toUnmodifiableList() with null -> NullPointerException
joining(): httphttpsmysqlssh
joining(", "): http, https, mysql, ssh
joining as JSON array: ["http","https","mysql","ssh"]
joining on empty: '[]'
allow ssh http https from 10.0.0.0/8
*/
