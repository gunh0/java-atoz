// Method overriding: a subclass provides its own implementation of an
// instance method inherited from its superclass, with the same name and
// parameter list. Which version runs is decided at RUNTIME by the object.
// Rules:
//  - @Override asks the compiler to check you really override something
//  - the return type may be a subtype (covariant return)
//  - access cannot be narrowed (public stays public), and the overriding method
//    cannot throw broader checked exceptions
//  - static, private and final methods are not overridden; a static method
//    with the same signature only HIDES the parent's one
public class MethodOverridingExample {
    public static void main(String[] args) {
        Shape[] shapes = { new Shape("blob"), new Circle(2), new Square(3) };
        for (Shape s : shapes) {
            System.out.printf("%-8s area=%.2f%n", s.name(), s.area());
        }

        // Covariant return: Square.copy() is declared to return Square
        Square original = new Square(5);
        Square copy = original.copy();          // no cast needed
        System.out.println("copy side: " + copy.side);

        // @Override catches typos: a method named "aera" would be a new method,
        // and annotating it with @Override would fail to compile.

        // Static methods are hidden, not overridden: the declared type decides
        Shape declaredAsShape = new Circle(1);
        System.out.println("static via Shape type:  " + declaredAsShape.category());
        System.out.println("static via Circle type: " + Circle.category());
        System.out.println("instance via Shape type: " + declaredAsShape.name());
    }
}

class Shape {
    private final String name;

    Shape(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public double area() {
        return 0;
    }

    public Shape copy() {
        return new Shape(name);
    }

    static String category() {
        return "generic shape";
    }
}

class Circle extends Shape {
    final double radius;

    Circle(double radius) {
        super("circle");
        this.radius = radius;
    }

    @Override
    public double area() {
        return Math.PI * radius * radius;
    }

    // `double area()` with no `public` would not compile: it narrows access

    static String category() {        // hides Shape.category(); no @Override allowed
        return "round shape";
    }
}

class Square extends Shape {
    final double side;

    Square(double side) {
        super("square");
        this.side = side;
    }

    @Override
    public double area() {
        return side * side;
    }

    @Override
    public Square copy() {            // covariant return type
        return new Square(side);
    }
}

/* Output:
blob     area=0.00
circle   area=12.57
square   area=9.00
copy side: 5.0
static via Shape type:  generic shape
static via Circle type: round shape
instance via Shape type: circle
*/
