// JEP 441 — Pattern Matching for switch (Java 21)
//
// switch used to accept only numbers, strings and enum constants; choosing
// behaviour by TYPE meant an if/else-if chain of instanceof tests, where
// the compiler cannot tell you that a case is missing or unreachable, and
// switch threw NullPointerException on null. Java 21 lets case labels be
// patterns (type patterns and record patterns), refined with `when`
// guards, and adds `case null`. Over a sealed hierarchy the compiler
// checks exhaustiveness, so no `default` is needed and adding a new
// subtype turns every incomplete switch into a compile error instead of a
// silent fall-through. Cases are tested top to bottom, and a case
// dominated by an earlier one is rejected.
import java.util.Arrays;
import java.util.List;

public class PatternMatchingForSwitch {
    // A small sealed model of authentication events
    sealed interface AuthEvent permits Login, Logout, TokenRefresh {
    }

    record Login(String user, boolean success, int failuresToday) implements AuthEvent {
    }

    record Logout(String user) implements AuthEvent {
    }

    record TokenRefresh(String user, long ttlSeconds) implements AuthEvent {
    }

    public static void main(String[] args) {
        List<AuthEvent> events = List.of(
                new Login("alice", true, 0),
                new Login("mallory", false, 7),
                new Login("bob", false, 1),
                new TokenRefresh("alice", 30),
                new TokenRefresh("bob", 3600),
                new Logout("alice"));

        for (AuthEvent e : events) {
            System.out.printf("%-52s before: %-22s after: %s%n", e, before(e), after(e));
        }

        // Any type can be switched on now, and null gets its own case
        for (Object o : Arrays.asList(42, -1, "  ", "token", new int[]{1, 2}, null)) {
            System.out.println("classify(" + (o instanceof int[] a ? "int[" + a.length + "]" : o) + ") = " + classify(o));
        }
    }

    // Before: an instanceof chain; forgetting a type compiles fine and falls to the end
    static String before(AuthEvent e) {
        if (e instanceof Login l) {
            if (!l.success() && l.failuresToday() > 5) {
                return "lock account " + l.user();
            } else if (!l.success()) {
                return "count failure";
            }
            return "welcome " + l.user();
        } else if (e instanceof Logout lo) {
            return "bye " + lo.user();
        } else if (e instanceof TokenRefresh t) {
            return t.ttlSeconds() < 60 ? "refresh soon" : "ok";
        }
        throw new IllegalStateException("unknown event"); // the compiler cannot help here
    }

    // After: exhaustive over the sealed interface, no default; guards with `when`
    static String after(AuthEvent e) {
        return switch (e) {
            case Login(String user, boolean ok, int fails) when !ok && fails > 5 -> "lock account " + user;
            case Login l when !l.success() -> "count failure";
            case Login l -> "welcome " + l.user();
            case Logout(String user) -> "bye " + user;
            case TokenRefresh t when t.ttlSeconds() < 60 -> "refresh soon";
            case TokenRefresh t -> "ok";
        };
    }

    static String classify(Object o) {
        return switch (o) {
            case null -> "null, handled without NullPointerException";
            case Integer i when i < 0 -> "negative int";
            case Integer i -> "int " + i;
            case String s when s.isBlank() -> "blank string";
            case String s -> "string of length " + s.length();
            case int[] arr -> "int array, sum " + Arrays.stream(arr).sum();
            // a second `case Integer i` here would not compile: it is dominated by the one above
            default -> "other: " + o.getClass().getSimpleName();
        };
    }
}

/* Output:
Login[user=alice, success=true, failuresToday=0]     before: welcome alice          after: welcome alice
Login[user=mallory, success=false, failuresToday=7]  before: lock account mallory   after: lock account mallory
Login[user=bob, success=false, failuresToday=1]      before: count failure          after: count failure
TokenRefresh[user=alice, ttlSeconds=30]              before: refresh soon           after: refresh soon
TokenRefresh[user=bob, ttlSeconds=3600]              before: ok                     after: ok
Logout[user=alice]                                   before: bye alice              after: bye alice
classify(42) = int 42
classify(-1) = negative int
classify(  ) = blank string
classify(token) = string of length 5
classify(int[2]) = int array, sum 3
classify(null) = null, handled without NullPointerException
*/
