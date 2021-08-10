// JEP 394 — Pattern Matching for instanceof (Java 16)
//
// The test-then-cast idiom repeats the type three times:
//     if (obj instanceof String) { String s = (String) obj; ... }
// A type pattern tests and binds in one step: `obj instanceof String s`.
// The binding variable is in scope only where the match is certain (flow
// scoping): inside the if, after a negated test that returns early, and on
// the right of &&. Since Java 16 pattern variables are not implicitly final,
// and a pattern that is always true (the expression already has that type)
// is a compile-time error.
import java.util.List;

public class PatternMatchingInstanceof {
    public static void main(String[] args) {
        List<Object> values = List.of("hello", 42, 3.5, List.of(1, 2), 'c');
        for (Object value : values) {
            System.out.println(describeOld(value) + " | " + describe(value));
        }

        System.out.println(new Point(1, 2).equals(new Point(1, 2)));
        System.out.println(firstWordLength("pattern matching"));
        System.out.println(firstWordLength(7));
    }

    // Before: test, then cast
    static String describeOld(Object obj) {
        if (obj instanceof String) {
            String s = (String) obj;
            return "String of length " + s.length();
        } else if (obj instanceof Integer) {
            Integer i = (Integer) obj;
            return "Integer, doubled " + (i * 2);
        }
        return "other";
    }

    // After: test and bind
    static String describe(Object obj) {
        if (obj instanceof String s) {
            return "String of length " + s.length();
        } else if (obj instanceof Integer i) {
            return "Integer, doubled " + (i * 2);
        } else if (obj instanceof Double d && d > 1.0) { // binding usable after &&
            return "Double above 1.0";
        } else if (obj instanceof List<?> list && !list.isEmpty()) {
            return "List starting with " + list.get(0);
        }
        return "other (" + obj.getClass().getSimpleName() + ")";
    }

    // Flow scoping: after a negated test that exits, the binding is in scope
    static int firstWordLength(Object obj) {
        if (!(obj instanceof String text)) {
            return -1;
        }
        int space = text.indexOf(' ');
        return space < 0 ? text.length() : space;
    }

    // A tidier equals
    static final class Point {
        private final int x, y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Point p && p.x == x && p.y == y;
        }

        @Override
        public int hashCode() {
            return 31 * x + y;
        }
    }
}

/* Output:
String of length 5 | String of length 5
Integer, doubled 84 | Integer, doubled 84
other | Double above 1.0
other | List starting with 1
other | other (Character)
true
7
-1
*/
