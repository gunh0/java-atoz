// Optional<T> is a container that holds either one non-null value or nothing.
// It is meant as a RETURN type for "may have no result" (findUser, firstMatch),
// so the caller has to decide what absence means instead of forgetting a
// null check. Creating:   Optional.of(v) (v must not be null),
//                         Optional.ofNullable(v), Optional.empty()
// Unwrapping:  orElse(default)       default is always evaluated
//              orElseGet(supplier)   supplier runs only when empty
//              orElseThrow()         NoSuchElementException, or your exception
//              ifPresent / ifPresentOrElse, isPresent / isEmpty
// Avoid get() without a check, Optional fields/parameters, and Optional<List>.
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

public class OptionalBasicsExample {
    record User(String name, String email) {}

    static final Map<String, User> USERS = Map.of(
            "alice", new User("alice", "alice@example.com"),
            "bob", new User("bob", null));

    // The method signature itself says "there may be no such user"
    static Optional<User> findUser(String name) {
        return Optional.ofNullable(USERS.get(name));
    }

    static String loadDefaultTheme() {
        System.out.println("  (loading default theme)");
        return "light";
    }

    public static void main(String[] args) {
        System.out.println("findUser(alice) = " + findUser("alice"));
        System.out.println("findUser(mallory) = " + findUser("mallory"));

        // of() rejects null right away, ofNullable() turns it into empty
        try {
            Optional.of(USERS.get("mallory"));
        } catch (NullPointerException e) {
            System.out.println("Optional.of(null) -> NullPointerException");
        }
        System.out.println("ofNullable(null).isEmpty() = " + Optional.ofNullable(null).isEmpty());

        // orElse evaluates its argument even when a value is present
        Optional<String> theme = Optional.of("dark");
        System.out.println("orElse:");
        System.out.println("  theme = " + theme.orElse(loadDefaultTheme()));
        System.out.println("orElseGet:");
        System.out.println("  theme = " + theme.orElseGet(OptionalBasicsExample::loadDefaultTheme));
        System.out.println("orElseGet on empty:");
        System.out.println("  theme = " + Optional.<String>empty().orElseGet(OptionalBasicsExample::loadDefaultTheme));

        // orElseThrow: absence is an error here
        try {
            findUser("mallory").orElseThrow();
        } catch (NoSuchElementException e) {
            System.out.println("orElseThrow() -> NoSuchElementException: " + e.getMessage());
        }
        try {
            findUser("mallory").orElseThrow(() -> new IllegalArgumentException("unknown user: mallory"));
        } catch (IllegalArgumentException e) {
            System.out.println("orElseThrow(supplier) -> " + e.getMessage());
        }

        // Acting on the value without unwrapping it
        findUser("alice").ifPresent(u -> System.out.println("send reset link to " + u.email()));
        for (String name : new String[] {"alice", "mallory"}) {
            findUser(name).ifPresentOrElse(
                    u -> System.out.println(name + ": found"),
                    () -> System.out.println(name + ": not found, log failed lookup"));
        }

        // Optionals compare by content
        System.out.println("Optional.of(1).equals(Optional.of(1)) = " + Optional.of(1).equals(Optional.of(1)));
        System.out.println("empty().equals(ofNullable(null)) = " + Optional.empty().equals(Optional.ofNullable(null)));
    }
}

/* Output:
findUser(alice) = Optional[User[name=alice, email=alice@example.com]]
findUser(mallory) = Optional.empty
Optional.of(null) -> NullPointerException
ofNullable(null).isEmpty() = true
orElse:
  (loading default theme)
  theme = dark
orElseGet:
  theme = dark
orElseGet on empty:
  (loading default theme)
  theme = light
orElseThrow() -> NoSuchElementException: No value present
orElseThrow(supplier) -> unknown user: mallory
send reset link to alice@example.com
alice: found
mallory: not found, log failed lookup
Optional.of(1).equals(Optional.of(1)) = true
empty().equals(ofNullable(null)) = true
*/
