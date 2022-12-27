// A bounded type parameter restricts which types may be used for T:
//   <T extends Number>                   T must be Number or a subclass
//   <T extends Comparable<? super T>>    T must be comparable with itself
//   <T extends Shape & Comparable<T>>    several bounds: one class first, then interfaces
// Inside the class or method the bound is what lets you CALL methods on T
// (doubleValue(), compareTo(), area()); without it T is only an Object.
// The bound is also checked at compile time: Stats<String> does not compile.
// Note that "extends" is used for interfaces too.
import java.util.List;

public class BoundedTypesExample {
    public static void main(String[] args) {
        Stats<Integer> ints = new Stats<>(List.of(3, 9, 4));
        Stats<Double> doubles = new Stats<>(List.of(1.5, 2.5));
        System.out.println("int average = " + ints.average());
        System.out.println("double average = " + doubles.average());
        // Stats<String> s = new Stats<>(List.of("a")); // compile error: String is not a Number

        // Comparable bound: works for any self-comparable type
        System.out.println("max of ints = " + max(List.of(3, 17, 8)));
        System.out.println("max of strings = " + max(List.of("pear", "apple", "zucchini")));
        System.out.println("clamp(150, 0, 100) = " + clamp(150, 0, 100));
        System.out.println("clamp(\"m\", \"a\", \"f\") = " + clamp("m", "a", "f"));

        // Multiple bounds: a class and an interface
        List<Square> squares = List.of(new Square(3), new Square(1), new Square(2));
        Square largest = largest(squares);
        System.out.println("largest square side = " + largest.side + ", area = " + largest.area());
    }

    static <T extends Comparable<? super T>> T max(List<T> items) {
        T best = items.get(0);
        for (T item : items) {
            if (item.compareTo(best) > 0) {
                best = item;
            }
        }
        return best;
    }

    static <T extends Comparable<? super T>> T clamp(T value, T low, T high) {
        if (value.compareTo(low) < 0) return low;
        if (value.compareTo(high) > 0) return high;
        return value;
    }

    // T must be a Shape (so area() exists) AND Comparable (so compareTo exists)
    static <T extends Shape & Comparable<T>> T largest(List<T> shapes) {
        T best = shapes.get(0);
        for (T s : shapes) {
            if (s.compareTo(best) > 0) {
                best = s;
            }
        }
        return best;
    }
}

// The bound makes doubleValue() available on every element
class Stats<T extends Number> {
    private final List<T> values;

    Stats(List<T> values) {
        this.values = values;
    }

    double average() {
        double sum = 0;
        for (T v : values) {
            sum += v.doubleValue();
        }
        return sum / values.size();
    }
}

abstract class Shape {
    abstract double area();
}

class Square extends Shape implements Comparable<Square> {
    final int side;

    Square(int side) {
        this.side = side;
    }

    @Override
    double area() {
        return side * side;
    }

    @Override
    public int compareTo(Square other) {
        return Double.compare(area(), other.area());
    }
}

/* Output:
int average = 5.333333333333333
double average = 2.0
max of ints = 17
max of strings = zucchini
clamp(150, 0, 100) = 100
clamp("m", "a", "f") = f
largest square side = 3, area = 9.0
*/
