// Effective Java, Item 42: Prefer lambdas to anonymous classes.
//
// Before Java 8, a function object meant an anonymous class: five lines of
// ceremony around one expression. A lambda expresses the same thing for any
// functional interface and lets the compiler infer the parameter types. Use
// lambdas for short behaviour (one to three lines); if it needs a name or
// documentation, write a method. Anonymous classes are still needed for
// abstract classes, for interfaces with several abstract methods, and when
// the function object must refer to itself: inside a lambda, "this" is the
// enclosing instance, inside an anonymous class it is the anonymous object.
// Also: do not serialize lambdas or anonymous classes.
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.DoubleBinaryOperator;

public class LambdasOverAnonymousClasses {
    public static void main(String[] args) {
        List<String> words = new ArrayList<>(List.of("banana", "fig", "cherry", "kiwi", "apple"));

        // Anonymous class: the old way
        Collections.sort(words, new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                return Integer.compare(s1.length(), s2.length());
            }
        });
        System.out.println("anonymous class: " + words);

        // Lambda: types inferred from Comparator<String>
        Collections.shuffle(words, new java.util.Random(1));
        Collections.sort(words, (s1, s2) -> Integer.compare(s1.length(), s2.length()));
        System.out.println("lambda:          " + words);

        // Even shorter with a comparator construction method and List.sort
        Collections.shuffle(words, new java.util.Random(1));
        words.sort(Comparator.comparingInt(String::length));
        System.out.println("comparingInt:    " + words);

        // Constant-specific behaviour without constant-specific class bodies
        for (Operation op : Operation.values()) {
            System.out.printf("6 %s 3 = %s%n", op, op.apply(6, 3));
        }

        new LambdasOverAnonymousClasses().whatIsThis();
    }

    void whatIsThis() {
        Runnable anonymous = new Runnable() {
            @Override
            public void run() {
                System.out.println("anonymous class: this is " + this.getClass().getName());
            }
        };
        Runnable lambda = () -> System.out.println("lambda:          this is " + this.getClass().getName());
        anonymous.run();
        lambda.run();
    }
}

enum Operation {
    PLUS("+", (x, y) -> x + y),
    MINUS("-", (x, y) -> x - y),
    TIMES("*", (x, y) -> x * y),
    DIVIDE("/", (x, y) -> x / y);

    private final String symbol;
    private final DoubleBinaryOperator op;

    Operation(String symbol, DoubleBinaryOperator op) {
        this.symbol = symbol;
        this.op = op;
    }

    double apply(double x, double y) {
        return op.applyAsDouble(x, y);
    }

    @Override
    public String toString() {
        return symbol;
    }
}

/* Output:
anonymous class: [fig, kiwi, apple, banana, cherry]
lambda:          [fig, kiwi, apple, banana, cherry]
comparingInt:    [fig, kiwi, apple, banana, cherry]
6 + 3 = 9.0
6 - 3 = 3.0
6 * 3 = 18.0
6 / 3 = 2.0
anonymous class: this is LambdasOverAnonymousClasses$2
lambda:          this is LambdasOverAnonymousClasses
*/
