// Unnamed variables and patterns (final in Java 22): when a declaration is
// required by the syntax but its value is never used, name it _ (a single
// underscore). It says "intentionally unused" to readers and tools, and _ can
// be declared any number of times in the same scope.
// Allowed for: local variables, for-each and basic for variables, catch
// parameters, try-with-resources resources, lambda parameters, and inside
// patterns: `case Point(int x, _)` ignores a record component, and
// `case Circle _` matches a type without binding a variable. Several patterns
// without bindings can share one case label: `case Circle _, Square _ ->`.
// Not allowed: fields, method parameters, or reading _ as an expression.
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;

public class UnnamedVariablesExample {
    sealed interface Shape permits Circle, Square, Rect { }
    record Circle(double r) implements Shape { }
    record Square(double side) implements Shape { }
    record Rect(double w, double h) implements Shape { }

    record Point(int x, int y) { }
    record Line(Point from, Point to) { }

    static int parseOrDefault(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException _) {        // exception object not needed
            return fallback;
        }
    }

    static String kind(Shape s) {
        return switch (s) {
            case Circle _, Square _ -> "regular";   // one label, several type patterns
            case Rect(var w, var h) when w == h -> "square-ish rect";
            case Rect _ -> "rectangle";
        };
    }

    static String horizontal(Object o) {
        // Nested record pattern, ignoring the components we don't care about
        if (o instanceof Line(Point(_, int y1), Point(_, int y2)) && y1 == y2) {
            return "horizontal at y=" + y1;
        }
        return "not a horizontal line";
    }

    static class Lock implements AutoCloseable {
        Lock() { System.out.println("  lock acquired"); }
        public void close() { System.out.println("  lock released"); }
    }

    public static void main(String[] args) {
        System.out.println("parse \"42\" -> " + parseOrDefault("42", -1) + ", parse \"x\" -> " + parseOrDefault("x", -1));

        for (Shape s : List.of(new Circle(1), new Square(2), new Rect(3, 3), new Rect(2, 5))) {
            System.out.println(s + " is " + kind(s));
        }

        System.out.println(horizontal(new Line(new Point(0, 5), new Point(9, 5))));
        System.out.println(horizontal(new Line(new Point(0, 5), new Point(9, 6))));

        // for-each where only the number of iterations matters
        int count = 0;
        for (var _ : List.of("a", "b", "c")) count++;
        System.out.println("counted " + count + " elements");

        // Local variable for a side effect only; two _ in the same scope are fine
        Queue<String> jobs = new ArrayDeque<>(List.of("header", "job-1", "job-2"));
        var _ = jobs.remove();                       // skip the header
        String _ = "also unused";
        System.out.println("remaining jobs: " + jobs);

        // Lambda parameters: BiFunction-like callbacks that ignore an argument
        Map<String, Integer> logins = new TreeMap<>(Map.of("alice", 3, "bob", 0, "carol", 7));
        logins.replaceAll((_, n) -> Math.min(n, 5));  // key not needed
        System.out.println("capped: " + logins);
        StringBuilder names = new StringBuilder();
        logins.forEach((name, _) -> names.append(name).append(' '));  // value not needed
        System.out.println("names: " + names.toString().strip());

        // try-with-resources where the resource is only held for its scope
        try (var _ = new Lock()) {
            System.out.println("  critical section");
        }
    }
}

/* Output:
parse "42" -> 42, parse "x" -> -1
Circle[r=1.0] is regular
Square[side=2.0] is regular
Rect[w=3.0, h=3.0] is square-ish rect
Rect[w=2.0, h=5.0] is rectangle
horizontal at y=5
not a horizontal line
counted 3 elements
remaining jobs: [job-1, job-2]
capped: {alice=3, bob=0, carol=5}
names: alice bob carol
  lock acquired
  critical section
  lock released
*/
