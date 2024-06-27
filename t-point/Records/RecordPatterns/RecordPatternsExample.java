// A record pattern deconstructs a record into its components in one step:
// `obj instanceof Point(int x, int y)` tests the type and binds x and y via
// the accessors. Patterns nest, so `Line(Point(var x1, var y1), Point p2)`
// reaches two levels down, and `var` lets the compiler infer component types.
// They work in instanceof and in switch, where they combine with guards
// (`when`) and with sealed types for exhaustive matching.
// A nested pattern only matches non-null components: a Line whose start is
// null does not match Line(Point(...), ...) and falls through.
import java.util.List;

public class RecordPatternsExample {

    record Point(int x, int y) { }

    record Line(Point start, Point end) { }

    record Circle(Point center, int radius) { }

    record Labeled(String label, Object shape) { }

    static String describe(Object obj) {
        return switch (obj) {
            case Point(var x, var y) when x == 0 && y == 0 -> "origin";
            case Point(int x, int y) -> "point (" + x + "," + y + ")";
            case Line(Point(var x1, var y1), Point(var x2, var y2)) when x1 == x2 ->
                    "vertical line at x=" + x1 + ", length " + Math.abs(y2 - y1);
            case Line(Point(var x1, var y1), Point(var x2, var y2)) when y1 == y2 ->
                    "horizontal line at y=" + y1 + ", length " + Math.abs(x2 - x1);
            case Line(Point s, Point e) -> "line " + s + " -> " + e;
            case Circle(Point(var x, var y), var r) -> "circle r=" + r + " at (" + x + "," + y + ")";
            // Patterns nest through any record, including an Object component
            case Labeled(String label, Circle(var c, var r)) -> label + ": a circle of radius " + r;
            case Labeled(String label, var other) -> label + ": " + describe(other);
            case null -> "null";
            default -> "unknown " + obj.getClass().getSimpleName();
        };
    }

    public static void main(String[] args) {
        List<Object> shapes = List.of(
                new Point(0, 0),
                new Point(3, 4),
                new Line(new Point(2, 1), new Point(2, 9)),
                new Line(new Point(1, 5), new Point(7, 5)),
                new Line(new Point(0, 0), new Point(3, 3)),
                new Circle(new Point(1, 1), 10),
                new Labeled("sun", new Circle(new Point(0, 0), 700)),
                new Labeled("corner", new Point(9, 9)),
                "a string");
        for (Object shape : shapes) {
            System.out.println(describe(shape));
        }
        System.out.println(describe(null));

        // instanceof with a nested record pattern: no casts, no accessor calls
        Object o = new Line(new Point(1, 2), new Point(4, 6));
        if (o instanceof Line(Point(var x1, var y1), Point(var x2, var y2))) {
            double length = Math.hypot(x2 - x1, y2 - y1);
            System.out.println("length of " + o + " = " + length);
        }

        // A null component does not match the nested Point pattern
        Object broken = new Line(null, new Point(1, 1));
        System.out.println("Line(null, ...) matches Line(Point(var a, var b), var e): "
                + (broken instanceof Line(Point(var a, var b), var e)));
        System.out.println("Line(null, ...) matches Line(var s, var e):                  "
                + (broken instanceof Line(var s, var e)));
    }
}

/* Output:
origin
point (3,4)
vertical line at x=2, length 8
horizontal line at y=5, length 6
line Point[x=0, y=0] -> Point[x=3, y=3]
circle r=10 at (1,1)
sun: a circle of radius 700
corner: point (9,9)
unknown String
null
length of Line[start=Point[x=1, y=2], end=Point[x=4, y=6]] = 5.0
Line(null, ...) matches Line(Point(var a, var b), var e): false
Line(null, ...) matches Line(var s, var e):                  true
*/
