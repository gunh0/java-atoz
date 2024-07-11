// Interfaces can hold more than abstract methods:
//  - default methods give implementations a behaviour they may override
//  - static methods belong to the interface itself; they are NOT inherited,
//    so they are called as Validator.of(...), never through an implementing type
//  - private (and private static) methods share code between defaults and
//    statics without making that helper part of the public API
// When a class inherits the same default method from two interfaces, it must
// override it and may pick one with InterfaceName.super.method().
// A method from a superclass always wins over an interface default.
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InterfacePrivateMethodsExample {

    interface Validator {
        // The single abstract method: returns problems, empty if valid
        List<String> problems(String input);

        default boolean isValid(String input) {
            return problems(normalize(input)).isEmpty();
        }

        default String report(String input) {
            List<String> found = problems(normalize(input));
            return found.isEmpty() ? "ok" : join(found);
        }

        // Static factories: the interface is also the natural place for its helpers
        static Validator maxLength(int max) {
            return s -> s.length() > max ? List.of("longer than " + max) : List.of();
        }

        static Validator noneOf(String chars) {
            return s -> {
                List<String> out = new ArrayList<>();
                for (char c : chars.toCharArray()) {
                    if (s.indexOf(c) >= 0) {
                        out.add("contains '" + c + "'");
                    }
                }
                return out;
            };
        }

        static Validator all(Validator... validators) {
            return s -> {
                List<String> out = new ArrayList<>();
                for (Validator v : validators) {
                    out.addAll(v.problems(s));
                }
                return out;
            };
        }

        // Private helpers shared by the default methods: not visible to implementors
        private String normalize(String input) {
            return input == null ? "" : input.strip();
        }

        private static String join(List<String> parts) {
            return String.join("; ", parts);
        }
    }

    interface Auditable {
        default String describe() {
            return "auditable";
        }
    }

    interface Cacheable {
        default String describe() {
            return "cacheable";
        }
    }

    // Two defaults with the same signature: the class must resolve the conflict
    static class UserRecord implements Auditable, Cacheable {
        @Override
        public String describe() {
            return Auditable.super.describe() + " + " + Cacheable.super.describe();
        }
    }

    static class Base {
        public String describe() {
            return "from superclass";
        }
    }

    // Class wins: Base.describe() beats the interface default
    static class Derived extends Base implements Auditable { }

    public static void main(String[] args) {
        Validator username = Validator.all(Validator.maxLength(8), Validator.noneOf("<>'\""));
        for (String input : new String[] {"alice", "  bob  ", "<script>", "a_very_long_name'"}) {
            System.out.printf(Locale.ROOT, "%-20s valid=%-5b %s%n",
                    "\"" + input + "\"", username.isValid(input), username.report(input));
        }

        System.out.println(new UserRecord().describe());
        System.out.println(new Derived().describe());

        // Reflection shows the private helpers are real methods, just not public API
        // (getDeclaredMethods has no defined order, so sort; skip compiler-generated lambda bodies)
        java.util.Arrays.stream(Validator.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPrivate(m.getModifiers()) && !m.isSynthetic())
                .map(m -> "private helper: " + m.getName()
                        + (java.lang.reflect.Modifier.isStatic(m.getModifiers()) ? " (static)" : ""))
                .sorted()
                .forEach(System.out::println);
    }
}

/* Output:
"alice"              valid=true  ok
"  bob  "            valid=true  ok
"<script>"           valid=false contains '<'; contains '>'
"a_very_long_name'"  valid=false longer than 8; contains '''
auditable + cacheable
from superclass
private helper: join (static)
private helper: normalize
*/
