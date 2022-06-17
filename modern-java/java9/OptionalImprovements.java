// Java 9 — Optional.ifPresentOrElse, Optional.or, Optional.stream
// (API additions, no JEP)
//
// Java 8's Optional covered the basics, but three common needs still pushed
// people back to isPresent()/get(): doing one thing when a value is present
// and another when it is not, falling back to ANOTHER Optional (a second
// lookup) without unwrapping, and turning a stream of Optionals into a
// stream of the present values. Java 9 added a method for each:
// ifPresentOrElse(action, emptyAction), or(supplier) and stream().
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class OptionalImprovements {
    static final Map<String, String> CACHE = Map.of("kim", "Seoul");
    static final Map<String, String> DATABASE = Map.of("kim", "Seoul (db)", "lee", "Busan");

    static Optional<String> fromCache(String user) {
        return Optional.ofNullable(CACHE.get(user));
    }

    static Optional<String> fromDatabase(String user) {
        System.out.println("  (database lookup for " + user + ")");
        return Optional.ofNullable(DATABASE.get(user));
    }

    public static void main(String[] args) {
        // ifPresentOrElse: both branches without isPresent()/get()
        for (String user : List.of("kim", "park")) {
            Optional<String> city = fromCache(user);
            // Before
            if (city.isPresent()) {
                System.out.println("before: " + user + " lives in " + city.get());
            } else {
                System.out.println("before: " + user + " not cached");
            }
            // After
            city.ifPresentOrElse(
                    c -> System.out.println("after:  " + user + " lives in " + c),
                    () -> System.out.println("after:  " + user + " not cached"));
        }

        // or: lazily fall back to another Optional-returning lookup
        for (String user : List.of("kim", "lee", "park")) {
            Optional<String> city = fromCache(user).or(() -> fromDatabase(user));
            System.out.println("or: " + user + " -> " + city.orElse("unknown"));
        }

        // stream: flatten present values
        List<Optional<String>> lookups = List.of(fromCache("kim"), fromCache("lee"), Optional.of("Incheon"));
        // Before
        List<String> before = lookups.stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
        // After
        List<String> after = lookups.stream()
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
        System.out.println("stream: before " + before + ", after " + after);
    }
}

/* Output:
before: kim lives in Seoul
after:  kim lives in Seoul
before: park not cached
after:  park not cached
or: kim -> Seoul
  (database lookup for lee)
or: lee -> Busan
  (database lookup for park)
or: park -> unknown
stream: before [Seoul, Incheon], after [Seoul, Incheon]
*/
