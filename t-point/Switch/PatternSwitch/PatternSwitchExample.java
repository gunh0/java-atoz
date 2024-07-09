// Since Java 21 a switch can match on types, not just constants:
//  - `case Integer i ->` tests the type and binds a variable
//  - `case String s when s.isBlank() ->` adds a guard
//  - `case null ->` handles null explicitly (otherwise switch throws NPE)
//  - order matters: a more general pattern may not come before a more
//    specific one it dominates (the compiler rejects that)
//  - a pattern switch must be exhaustive, usually via `default` or sealed types
// Java 22 adds unnamed patterns: `_` stands for a component you do not need,
// and several patterns without bindings can share one case.
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class PatternSwitchExample {

    sealed interface Event permits Login, Logout, Upload { }

    record Login(String user, String ip, boolean mfa) implements Event { }

    record Logout(String user) implements Event { }

    record Upload(String user, String file, long bytes) implements Event { }

    static String classify(Object value) {
        return switch (value) {
            case null -> "null";
            case Integer i when i < 0 -> "negative int " + i;
            case Integer i -> "int " + i;
            case Long l -> "long " + l;
            case String s when s.isBlank() -> "blank string";
            case String s -> "string of length " + s.length();
            case int[] a -> "int array " + Arrays.toString(a);
            case List<?> list when list.isEmpty() -> "empty list";
            case List<?> list -> "list of " + list.size();
            case Map<?, ?> map -> "map of " + map.size();
            default -> "other " + value.getClass().getSimpleName();
        };
    }

    // Unnamed patterns: ignore components we do not use
    static String audit(Event event) {
        return switch (event) {
            case Login(var user, _, var mfa) when !mfa -> "WARN  " + user + " logged in without MFA";
            case Login(var user, var ip, _) -> "INFO  " + user + " logged in from " + ip;
            case Upload(var user, _, var bytes) when bytes > 10_000_000 -> "WARN  " + user + " uploaded a large file";
            // Two patterns, no bindings, one shared case
            case Logout _, Upload _ -> "DEBUG routine event";
        };
    }

    public static void main(String[] args) {
        Object[] values = {42, -7, 10L, "", "hello", new int[] {1, 2}, List.of(), List.of(1, 2, 3),
                Map.of("k", "v"), 3.14, null};
        for (Object v : values) {
            System.out.println(classify(v));
        }

        List<Event> events = List.of(
                new Login("alice", "10.0.0.5", true),
                new Login("bob", "10.0.0.9", false),
                new Upload("alice", "report.pdf", 2_048),
                new Upload("carol", "dump.sql", 50_000_000),
                new Logout("alice"));
        for (Event e : events) {
            System.out.println(audit(e));
        }

        // Without `case null`, a pattern switch throws NullPointerException on null
        Object nothing = null;
        try {
            String s = switch (nothing) {
                case String str -> str;
                default -> "default";
            };
            System.out.println(s);
        } catch (NullPointerException e) {
            System.out.println("switch on null without case null -> NullPointerException");
        }
    }
}

/* Output:
int 42
negative int -7
long 10
blank string
string of length 5
int array [1, 2]
empty list
list of 3
map of 1
other Double
null
INFO  alice logged in from 10.0.0.5
WARN  bob logged in without MFA
DEBUG routine event
WARN  carol uploaded a large file
DEBUG routine event
switch on null without case null -> NullPointerException
*/
