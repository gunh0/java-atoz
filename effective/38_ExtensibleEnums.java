// Effective Java, Item 38: Emulate extensible enums with interfaces.
//
// Enum types cannot be extended, which is almost always what you want. The
// exception is an "opcode"-like enum where API users should be able to add
// their own operations. The idiom: declare an interface for the behaviour,
// let the standard enum implement it, and write APIs against the interface.
// Clients then add new enums implementing the same interface. To accept a
// whole enum of operations, take either Class<T> with the bound
// <T extends Enum<T> & Operation> (gives values() via getEnumConstants) or a
// Collection<? extends Operation>. Implementations cannot inherit from each
// other, so share code through default methods or helpers.
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

public class ExtensibleEnums {
    public static void main(String[] args) {
        double x = 4, y = 2;

        // Class literal with the bounded type token: T is an enum AND an Operation
        test(BasicOperation.class, x, y);
        test(ExtendedOperation.class, x, y);

        // Or any collection of operations, mixing both enums
        List<Operation> mixed = List.of(BasicOperation.PLUS, ExtendedOperation.EXP, ExtendedOperation.REMAINDER);
        test(mixed, x, y);

        // EnumSet still works per enum type
        System.out.println("basic ops: " + EnumSet.allOf(BasicOperation.class));
        System.out.println("all symbols: " + Arrays.toString(
                java.util.stream.Stream.concat(Arrays.stream(BasicOperation.values()), Arrays.stream(ExtendedOperation.values()))
                        .map(Operation::symbol).toArray()));
    }

    static <T extends Enum<T> & Operation> void test(Class<T> opEnumType, double x, double y) {
        System.out.println(opEnumType.getSimpleName() + ":");
        for (Operation op : opEnumType.getEnumConstants()) {
            System.out.println("  " + op.describe(x, y));
        }
    }

    static void test(Collection<? extends Operation> opSet, double x, double y) {
        System.out.println("collection:");
        for (Operation op : opSet) {
            System.out.println("  " + op.describe(x, y));
        }
    }
}

interface Operation {
    String symbol();

    double apply(double x, double y);

    // Shared code lives in the interface, since enums cannot extend each other
    default String describe(double x, double y) {
        return String.format("%s %s %s = %s", fmt(x), symbol(), fmt(y), fmt(apply(x, y)));
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}

enum BasicOperation implements Operation {
    PLUS("+") {
        public double apply(double x, double y) { return x + y; }
    },
    MINUS("-") {
        public double apply(double x, double y) { return x - y; }
    },
    TIMES("*") {
        public double apply(double x, double y) { return x * y; }
    },
    DIVIDE("/") {
        public double apply(double x, double y) { return x / y; }
    };

    private final String symbol;

    BasicOperation(String symbol) {
        this.symbol = symbol;
    }

    @Override
    public String symbol() {
        return symbol;
    }
}

// Added by a client, without touching BasicOperation
enum ExtendedOperation implements Operation {
    EXP("^") {
        public double apply(double x, double y) { return Math.pow(x, y); }
    },
    REMAINDER("%") {
        public double apply(double x, double y) { return x % y; }
    };

    private final String symbol;

    ExtendedOperation(String symbol) {
        this.symbol = symbol;
    }

    @Override
    public String symbol() {
        return symbol;
    }
}

/* Output:
BasicOperation:
  4 + 2 = 6
  4 - 2 = 2
  4 * 2 = 8
  4 / 2 = 2
ExtendedOperation:
  4 ^ 2 = 16
  4 % 2 = 0
collection:
  4 + 2 = 6
  4 ^ 2 = 16
  4 % 2 = 0
basic ops: [PLUS, MINUS, TIMES, DIVIDE]
all symbols: [+, -, *, /, ^, %]
*/
