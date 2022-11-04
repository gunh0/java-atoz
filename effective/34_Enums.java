// Effective Java, Item 34: Use enums instead of int constants.
//
// The int enum pattern (static final int APPLE_FUJI = 0) has no type
// safety: an apple can be passed where an orange is expected, compared
// with ==, or replaced by any int. The values print as bare numbers and are
// compiled into clients, so changing them silently breaks code. Java enums
// are full classes: each constant is a singleton instance, the type is
// checked, they print their names, and they can carry fields and methods.
// When behaviour differs per constant, use constant-specific method bodies
// instead of a switch on this, so a new constant cannot forget a case.
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Enums {
    // int enum pattern
    static final int APPLE_FUJI = 0;
    static final int APPLE_PIPPIN = 1;
    static final int ORANGE_NAVEL = 0;
    static final int ORANGE_BLOOD = 1;

    enum Apple { FUJI, PIPPIN, GRANNY_SMITH }
    enum Orange { NAVEL, TEMPLE, BLOOD }

    public static void main(String[] args) {
        // Ints: comparing an apple to an orange compiles and is "true"
        System.out.println("int pattern: APPLE_FUJI == ORANGE_NAVEL is " + (APPLE_FUJI == ORANGE_NAVEL)
                + ", prints as " + APPLE_PIPPIN);
        // Enums: Apple.FUJI == Orange.NAVEL does not compile; names print
        System.out.println("enums: " + Apple.FUJI + ", " + Arrays.toString(Orange.values()));

        // Enum with data and behaviour
        double earthWeight = 70.0;
        double mass = earthWeight / Planet.EARTH.surfaceGravity();
        for (Planet p : Planet.values()) {
            System.out.printf("weight on %-7s %6.2f%n", p, p.surfaceWeight(mass));
        }

        // Constant-specific method implementations
        double x = 6, y = 3;
        for (Operation op : Operation.values()) {
            System.out.printf("%.0f %s %.0f = %.0f%n", x, op, y, op.apply(x, y));
        }
        System.out.println("fromString(\"*\"): " + Operation.fromString("*").map(Operation::name).orElse("none"));
        System.out.println("fromString(\"%\"): " + Operation.fromString("%").map(Operation::name).orElse("none"));
    }
}

enum Planet {
    MERCURY(3.302e+23, 2.439e6),
    VENUS(4.869e+24, 6.052e6),
    EARTH(5.975e+24, 6.378e6),
    MARS(6.419e+23, 3.393e6);

    private static final double G = 6.67300E-11;

    private final double mass;           // kg
    private final double radius;         // m
    private final double surfaceGravity; // m / s^2, precomputed

    Planet(double mass, double radius) {
        this.mass = mass;
        this.radius = radius;
        this.surfaceGravity = G * mass / (radius * radius);
    }

    double surfaceGravity() {
        return surfaceGravity;
    }

    double surfaceWeight(double mass) {
        return mass * surfaceGravity; // F = ma
    }
}

enum Operation {
    PLUS("+") {
        double apply(double x, double y) { return x + y; }
    },
    MINUS("-") {
        double apply(double x, double y) { return x - y; }
    },
    TIMES("*") {
        double apply(double x, double y) { return x * y; }
    },
    DIVIDE("/") {
        double apply(double x, double y) { return x / y; }
    };

    private static final Map<String, Operation> BY_SYMBOL =
            Arrays.stream(values()).collect(Collectors.toMap(Operation::toString, Function.identity()));

    private final String symbol;

    Operation(String symbol) {
        this.symbol = symbol;
    }

    // Every constant must implement it: forgetting one is a compile error
    abstract double apply(double x, double y);

    @Override
    public String toString() {
        return symbol;
    }

    static Optional<Operation> fromString(String symbol) {
        return Optional.ofNullable(BY_SYMBOL.get(symbol));
    }
}

/* Output:
int pattern: APPLE_FUJI == ORANGE_NAVEL is true, prints as 1
enums: FUJI, [NAVEL, TEMPLE, BLOOD]
weight on MERCURY  26.45
weight on VENUS    63.35
weight on EARTH    70.00
weight on MARS     26.57
6 + 3 = 9
6 - 3 = 3
6 * 3 = 18
6 / 3 = 2
fromString("*"): TIMES
fromString("%"): none
*/
