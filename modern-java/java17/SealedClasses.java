// JEP 409 — Sealed Classes (Java 17)
//
// Before sealed classes, a class hierarchy was either open to everyone
// (public, non-final) or closed to everyone (final); a package-private
// constructor was the only half-measure, and the compiler knew nothing about
// it. A sealed class or interface lists exactly which classes may extend it
// (`permits`). Each permitted subclass must say how it continues: final,
// sealed (with its own permits) or non-sealed (open again). This documents
// the design, stops unknown subtypes, and lets code reason about a closed set
// of cases. Records and enums fit naturally as permitted subtypes, because
// they are implicitly final.
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class SealedClasses {
    public static void main(String[] args) {
        List<Shape> shapes = List.of(
                new Circle(1.0),
                new Square(2.0),
                new Rectangle(2.0, 3.0),
                new FilledRectangle(1.0, 1.0, "red"),
                new TransparentRectangle(4.0, 0.5));

        for (Shape shape : shapes) {
            System.out.printf(Locale.ROOT, "%-20s area %.2f%n", shape.getClass().getSimpleName(), area(shape));
        }

        // The permitted subclasses are visible through reflection
        System.out.println("Shape is sealed: " + Shape.class.isSealed());
        System.out.println("Shape permits: " + Arrays.stream(Shape.class.getPermittedSubclasses())
                .map(Class::getSimpleName).toList());
        System.out.println("Rectangle permits: " + Arrays.stream(Rectangle.class.getPermittedSubclasses())
                .map(Class::getSimpleName).toList());

        // Not compilable: Triangle is not listed in Shape's permits clause
        // final class Triangle implements Shape { }
    }

    // Because the set of shapes is closed, every case can be listed here.
    // (Exhaustive `switch` over sealed types is still a preview feature in
    // Java 17, so this uses instanceof patterns.)
    static double area(Shape shape) {
        if (shape instanceof Circle c) {
            return Math.PI * c.radius() * c.radius();
        } else if (shape instanceof Square s) {
            return s.side() * s.side();
        } else if (shape instanceof Rectangle r) {
            return r.width() * r.height();
        }
        throw new AssertionError("unreachable for a sealed hierarchy: " + shape);
    }
}

sealed interface Shape permits Circle, Square, Rectangle {
}

// A record is implicitly final, so it may implement a sealed interface directly
record Circle(double radius) implements Shape {
}

final class Square implements Shape {
    private final double side;

    Square(double side) {
        this.side = side;
    }

    double side() {
        return side;
    }
}

// sealed again: Rectangle itself has a closed set of subclasses
sealed class Rectangle implements Shape permits FilledRectangle, TransparentRectangle {
    private final double width, height;

    Rectangle(double width, double height) {
        this.width = width;
        this.height = height;
    }

    double width() {
        return width;
    }

    double height() {
        return height;
    }
}

final class FilledRectangle extends Rectangle {
    private final String color;

    FilledRectangle(double width, double height, String color) {
        super(width, height);
        this.color = color;
    }

    String color() {
        return color;
    }
}

// non-sealed: anyone may extend this branch again
non-sealed class TransparentRectangle extends Rectangle {
    TransparentRectangle(double width, double height) {
        super(width, height);
    }
}

/* Output:
Circle               area 3.14
Square               area 4.00
Rectangle            area 6.00
FilledRectangle      area 1.00
TransparentRectangle area 2.00
Shape is sealed: true
Shape permits: [Circle, Square, Rectangle]
Rectangle permits: [FilledRectangle, TransparentRectangle]
*/
