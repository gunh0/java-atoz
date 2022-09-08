// Effective Java, Item 23: Prefer class hierarchies to tagged classes.
//
// A tagged class carries a "kind" field and switches on it in every method.
// It is verbose, error-prone and wasteful: fields for all kinds live in every
// instance, final fields cannot be used for kind-specific data, a forgotten
// case fails only at run time, and adding a kind means editing every switch.
// A class hierarchy gives each kind its own type with exactly the fields it
// needs, and the compiler checks that every kind implements every operation.
// With sealed interfaces (Java 17) and records the hierarchy is also short
// and closed, just like the tag set was.
import java.util.List;

public class TaggedClasses {
    public static void main(String[] args) {
        // Tagged class: one type, many shapes, switch everywhere
        List<TaggedFigure> tagged = List.of(TaggedFigure.circle(1), TaggedFigure.rectangle(2, 3));
        for (TaggedFigure f : tagged) {
            System.out.printf("tagged %-9s area=%.2f%n", f.shape, f.area());
        }
        try {
            TaggedFigure.triangle(3, 4).area(); // a new tag that area() never learned
        } catch (AssertionError e) {
            System.out.println("tagged TRIANGLE: " + e.getMessage());
        }

        // Hierarchy: each kind is a type; missing methods fail to compile
        List<Figure> figures = List.of(new Circle(1), new Rectangle(2, 3), new Square(2), new Triangle(3, 4));
        for (Figure f : figures) {
            System.out.printf("hierarchy %-9s area=%.2f%n", f.getClass().getSimpleName(), f.area());
        }
    }
}

// Anti-pattern
class TaggedFigure {
    enum Shape { RECTANGLE, CIRCLE, TRIANGLE }

    final Shape shape;
    double length, width; // used only for RECTANGLE (and TRIANGLE)
    double radius;        // used only for CIRCLE

    private TaggedFigure(Shape shape) {
        this.shape = shape;
    }

    static TaggedFigure circle(double radius) {
        TaggedFigure f = new TaggedFigure(Shape.CIRCLE);
        f.radius = radius;
        return f;
    }

    static TaggedFigure rectangle(double length, double width) {
        TaggedFigure f = new TaggedFigure(Shape.RECTANGLE);
        f.length = length;
        f.width = width;
        return f;
    }

    static TaggedFigure triangle(double base, double height) {
        TaggedFigure f = new TaggedFigure(Shape.TRIANGLE);
        f.length = base;
        f.width = height;
        return f;
    }

    double area() {
        switch (shape) {
            case RECTANGLE:
                return length * width;
            case CIRCLE:
                return Math.PI * (radius * radius);
            default:
                throw new AssertionError("no area for " + shape);
        }
    }
}

// Class hierarchy replacement
sealed interface Figure permits Circle, Rectangle, Square, Triangle {
    double area();
}

record Circle(double radius) implements Figure {
    public double area() {
        return Math.PI * (radius * radius);
    }
}

record Rectangle(double length, double width) implements Figure {
    public double area() {
        return length * width;
    }
}

record Square(double side) implements Figure {
    public double area() {
        return side * side;
    }
}

record Triangle(double base, double height) implements Figure {
    public double area() {
        return base * height / 2;
    }
}

/* Output:
tagged CIRCLE    area=3.14
tagged RECTANGLE area=6.00
tagged TRIANGLE: no area for TRIANGLE
hierarchy Circle    area=3.14
hierarchy Rectangle area=6.00
hierarchy Square    area=4.00
hierarchy Triangle  area=6.00
*/
