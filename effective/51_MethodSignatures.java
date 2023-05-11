// Effective Java, Item 51: Design method signatures carefully.
//
// A handful of habits make an API easier to learn and harder to misuse:
// choose names that follow the platform's conventions; do not offer a
// convenience method for every combination, but a small set of orthogonal
// ones that compose; keep parameter lists to four or fewer (split the
// method, introduce a helper class, or use a builder), especially when
// several parameters share a type and can be swapped silently; take
// interfaces rather than classes as parameter types; and prefer a
// two-element enum to a boolean, because THERMOMETER(CELSIUS) is readable at
// the call site and can grow a third constant later.
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class MethodSignatures {
    public static void main(String[] args) {
        // Boolean parameter: what does "true" mean here?
        System.out.println("boolean flag:  " + Thermometer.newInstance(true));
        // Enum parameter: self-documenting, and extensible
        System.out.println("enum:          " + Thermometer.newInstance(TemperatureScale.CELSIUS));
        System.out.println("enum:          " + Thermometer.newInstance(TemperatureScale.KELVIN));

        // Same-typed parameters are easy to swap without any compiler help
        System.out.println("rect(3, 5, 10, 20) vs rect(5, 3, 20, 10): "
                + rect(3, 5, 10, 20) + " / " + rect(5, 3, 20, 10));
        // A small parameter type makes the call unambiguous
        System.out.println("rect(Point, Size):  " + rect(new Point(3, 5), new Size(10, 20)));

        // Orthogonal building blocks: no need for List.indexOfInRange(...)
        List<String> letters = List.of("a", "b", "c", "b", "a");
        int index = letters.subList(2, 5).indexOf("b");
        System.out.println("index of b in [2, 5): " + (index < 0 ? -1 : index + 2));

        // Interface parameter: any Collection works, not just ArrayList
        System.out.println("distinct(List): " + distinct(new ArrayList<>(letters)));
        System.out.println("distinct(Set):  " + distinct(Set.of("z")));
    }

    static String rect(int x, int y, int width, int height) {
        return "(" + x + "," + y + " " + width + "x" + height + ")";
    }

    static String rect(Point origin, Size size) {
        return rect(origin.x(), origin.y(), size.width(), size.height());
    }

    static Set<String> distinct(Collection<String> items) {
        return new TreeSet<>(items);
    }
}

record Point(int x, int y) {
}

record Size(int width, int height) {
}

enum TemperatureScale {
    FAHRENHEIT, CELSIUS, KELVIN // KELVIN added later without breaking callers
}

class Thermometer {
    private final String unit;

    private Thermometer(String unit) {
        this.unit = unit;
    }

    static Thermometer newInstance(boolean isCelsius) { // which one is false?
        return new Thermometer(isCelsius ? "C" : "F");
    }

    static Thermometer newInstance(TemperatureScale scale) {
        return new Thermometer(switch (scale) {
            case FAHRENHEIT -> "F";
            case CELSIUS -> "C";
            case KELVIN -> "K";
        });
    }

    @Override
    public String toString() {
        return "Thermometer[" + unit + "]";
    }
}

/* Output:
boolean flag:  Thermometer[C]
enum:          Thermometer[C]
enum:          Thermometer[K]
rect(3, 5, 10, 20) vs rect(5, 3, 20, 10): (3,5 10x20) / (5,3 20x10)
rect(Point, Size):  (3,5 10x20)
index of b in [2, 5): 3
distinct(List): [a, b, c]
distinct(Set):  [z]
*/
