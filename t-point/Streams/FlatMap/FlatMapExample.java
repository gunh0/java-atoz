// flatMap(f) maps each element to a STREAM and concatenates those streams,
// turning a Stream<List<X>> or Stream<Group> into a flat Stream<X>. It is the
// tool for one-to-many: a user to their roles, a line to its words, an
// Optional to zero or one element.
// mapMulti (Java 16) does the same job imperatively: the function receives
// the element and a Consumer and pushes zero or more results into it. It
// avoids creating a small stream per element and is handy when only some
// elements produce output.
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class FlatMapExample {
    record User(String name, List<String> roles) {}

    record Grant(String user, String role) {}

    public static void main(String[] args) {
        List<User> users = List.of(
                new User("alice", List.of("admin", "dev")),
                new User("bob", List.of("dev")),
                new User("eve", List.of()));

        // map would give a Stream<List<String>>
        List<List<String>> nested = users.stream().map(User::roles).toList();
        System.out.println("map:     " + nested);
        // flatMap flattens it
        List<String> roles = users.stream().flatMap(u -> u.roles().stream()).distinct().toList();
        System.out.println("flatMap: " + roles);

        // Keeping the parent with each child: emit pairs
        List<Grant> grants = users.stream()
                .flatMap(u -> u.roles().stream().map(r -> new Grant(u.name(), r)))
                .toList();
        grants.forEach(g -> System.out.println("  " + g.user() + " -> " + g.role()));

        // Lines to words: a word count
        List<String> lines = List.of("deny from all", "allow from 10.0.0.0/8", "deny from 203.0.113.0/24");
        Map<String, Long> words = new TreeMap<>();
        lines.stream()
                .flatMap(line -> Stream.of(line.split(" ")))
                .forEach(w -> words.merge(w, 1L, Long::sum));
        System.out.println("word counts: " + words);

        // mapMulti: expand port ranges, skip invalid entries without making streams
        List<String> specs = List.of("22", "8000-8003", "bogus", "443");
        List<Integer> ports = specs.stream()
                .<Integer>mapMulti((spec, sink) -> expand(spec, sink))
                .toList();
        System.out.println("expanded ports: " + ports);
    }

    static void expand(String spec, Consumer<Integer> sink) {
        if (spec.matches("\\d+")) {
            sink.accept(Integer.parseInt(spec));
        } else if (spec.matches("\\d+-\\d+")) {
            String[] r = spec.split("-");
            for (int p = Integer.parseInt(r[0]); p <= Integer.parseInt(r[1]); p++) sink.accept(p);
        }
        // anything else: emit nothing
    }
}

/* Output:
map:     [[admin, dev], [dev], []]
flatMap: [admin, dev]
  alice -> admin
  alice -> dev
  bob -> dev
word counts: {10.0.0.0/8=1, 203.0.113.0/24=1, all=1, allow=1, deny=2, from=3}
expanded ports: [22, 8000, 8001, 8002, 8003, 443]
*/
