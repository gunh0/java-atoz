// JEP 440 — Record Patterns (Java 21)
//
// Java 16's pattern matching for instanceof tests a type and binds a
// variable, but with records you still had to call the accessors yourself,
// and nested data meant a cascade of instanceof checks, casts and null
// checks. A record pattern, Point(int x, int y), matches a record AND
// deconstructs it into its components in one step, and patterns nest:
// Line(Point(var x1, var y1), Point p2). It works in instanceof and in
// switch, components can use `var`, generic record types are inferred, and
// a nested record pattern never matches a null component (so no NPE).
import java.util.List;

public class RecordPatterns {
    record Point(int x, int y) {
    }

    enum Color { RED, GREEN }

    record ColoredPoint(Point p, Color c) {
    }

    record Rectangle(ColoredPoint upperLeft, ColoredPoint lowerRight) {
    }

    record Box<T>(T content) {
    }

    public static void main(String[] args) {
        Object shape = new Rectangle(new ColoredPoint(new Point(1, 5), Color.RED),
                new ColoredPoint(new Point(4, 2), Color.GREEN));

        // Before (Java 16): type pattern, then dig through accessors by hand
        if (shape instanceof Rectangle r) {
            ColoredPoint ul = r.upperLeft();
            if (ul != null && ul.p() != null) {
                System.out.println("before: upper-left x = " + ul.p().x() + ", color = " + ul.c());
            }
        }

        // After: nested record patterns name exactly the parts you need
        if (shape instanceof Rectangle(ColoredPoint(Point(var x, var y), Color c), ColoredPoint lr)) {
            System.out.println("after: upper-left (" + x + "," + y + ") " + c + ", lower-right color " + lr.c());
        }

        // Area of any rectangle, components bound directly
        System.out.println("area: " + area(shape));

        // Nulls: a nested RECORD pattern never matches a null component (no NPE) ...
        Object broken = new Rectangle(new ColoredPoint(null, Color.RED), null);
        System.out.println("broken rectangle matches Point(var x, var y) inside: "
                + (broken instanceof Rectangle(ColoredPoint(Point(var x, var y), var c), var lr)));
        // ... while a type pattern covering the component's type does match, binding null
        if (broken instanceof Rectangle(ColoredPoint(Point p, Color c), ColoredPoint lr)) {
            System.out.println("type patterns match it: p = " + p + ", c = " + c + ", lr = " + lr);
        }

        // Generic records: the type argument is inferred from the pattern
        Box<Box<String>> nested = new Box<>(new Box<>("secret"));
        if (nested instanceof Box(Box(var inner))) { // inner is inferred as String
            System.out.println("unboxed twice: " + inner.toUpperCase());
        }

        // In switch, record patterns combine with exhaustiveness and guards
        List<Object> things = List.of(new Point(0, 0), new Point(3, 3), new Point(2, 7), new Box<>(42), "text");
        for (Object o : things) {
            System.out.println(o + " -> " + describe(o));
        }
    }

    static int area(Object o) {
        if (o instanceof Rectangle(ColoredPoint(Point(var x1, var y1), var c1),
                                   ColoredPoint(Point(var x2, var y2), var c2))) {
            return Math.abs(x2 - x1) * Math.abs(y2 - y1);
        }
        return 0;
    }

    static String describe(Object o) {
        return switch (o) {
            case Point(int x, int y) when x == 0 && y == 0 -> "origin";
            case Point(int x, int y) when x == y -> "on the diagonal";
            case Point(int x, int y) -> "point at distance " + Math.hypot(x, y);
            case Box(Integer n) -> "box of the number " + n;
            case Box(var other) -> "box of " + other;
            default -> "something else";
        };
    }
}

/* Output:
before: upper-left x = 1, color = RED
after: upper-left (1,5) RED, lower-right color GREEN
area: 9
broken rectangle matches Point(var x, var y) inside: false
type patterns match it: p = null, c = RED, lr = null
unboxed twice: SECRET
Point[x=0, y=0] -> origin
Point[x=3, y=3] -> on the diagonal
Point[x=2, y=7] -> point at distance 7.280109889280518
Box[content=42] -> box of the number 42
text -> something else
*/
