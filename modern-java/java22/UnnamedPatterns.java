// JEP 456 — Unnamed Variables & Patterns (Java 22): unnamed patterns
//
// Record patterns (Java 21) deconstruct a record into all of its
// components, so matching on one field meant spelling out the others as
// `var x` bindings that were never used. Java 22 adds two shorthands:
// the unnamed pattern `_`, which matches any component (including null)
// without binding it, and unnamed pattern variables such as `Circle _`,
// which check a type but bind nothing. In a switch, several patterns that
// bind nothing can share one case label: `case Circle _, Square _ ->`.
import java.util.List;

public class UnnamedPatterns {
    sealed interface Shape permits Circle, Square, Rect, Group {}
    record Point(int x, int y) {}
    record Circle(Point center, int radius) implements Shape {}
    record Square(Point corner, int side) implements Shape {}
    record Rect(Point corner, int w, int h) implements Shape {}
    record Group(String name, List<Shape> members) implements Shape {}

    public static void main(String[] args) {
        List<Shape> shapes = List.of(
                new Circle(new Point(0, 0), 2),
                new Square(new Point(1, 1), 3),
                new Rect(new Point(0, 5), 4, 2),
                new Group("empty", List.of()));

        for (Shape s : shapes) {
            System.out.println(before(s) + " | " + after(s) + " | " + kind(s));
        }

        // `_` in instanceof: only the x coordinate matters here
        Object o = new Circle(new Point(7, 9), 1);
        if (o instanceof Circle(Point(int x, _), _)) {
            System.out.println("circle center x = " + x);
        }
    }

    // Before (Java 21): every component needs a binding, used or not
    static String before(Shape s) {
        return switch (s) {
            case Circle(Point(var x, var y), var r) -> "circle r=" + r;
            case Square(Point p, var side) -> "square side=" + side;
            case Rect(var corner, var w, var h) -> "rect " + w + "x" + h;
            case Group(var name, var members) -> "group of " + members.size();
        };
    }

    // After: `_` skips what the case does not use
    static String after(Shape s) {
        return switch (s) {
            case Circle(_, var r) -> "circle r=" + r;
            case Square(_, var side) -> "square side=" + side;
            case Rect(_, var w, var h) -> "rect " + w + "x" + h;
            case Group(_, var members) -> "group of " + members.size();
        };
    }

    // Several type patterns that bind nothing can share one case
    static String kind(Shape s) {
        return switch (s) {
            case Circle _, Square _ -> "regular";
            case Rect _ -> "irregular";
            case Group _ -> "composite";
        };
    }
}

/* Output:
circle r=2 | circle r=2 | regular
square side=3 | square side=3 | regular
rect 4x2 | rect 4x2 | irregular
group of 0 | group of 0 | composite
circle center x = 7
*/
