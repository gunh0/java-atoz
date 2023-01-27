// A lambda expression is an anonymous function: (parameters) -> body. It has
// no type of its own; the compiler gives it the type of the functional
// interface the context expects (target typing), so the same lambda text can
// be a Comparator in one place and a BiFunction in another.
//  - parameter types are usually inferred; parentheses are optional for one
//  - an expression body returns its value; a block body needs `return`
//  - a lambda may read local variables only if they are effectively final
//    (never reassigned), because it captures their value, not the variable
//  - `this` inside a lambda is the enclosing instance, unlike an anonymous class
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class LambdaSyntaxExample {
    private final String name = "outer instance";

    public static void main(String[] args) {
        // Same job, three ways: anonymous class, block lambda, expression lambda
        Comparator<String> byLengthAnon = new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return Integer.compare(a.length(), b.length());
            }
        };
        Comparator<String> byLengthBlock = (String a, String b) -> {
            return Integer.compare(a.length(), b.length());
        };
        Comparator<String> byLength = (a, b) -> Integer.compare(a.length(), b.length());

        List<String> hosts = new ArrayList<>(List.of("db-primary", "web", "cache01", "vpn"));
        hosts.sort(byLengthAnon);
        System.out.println("anonymous class: " + hosts);
        hosts.sort(byLengthBlock.reversed());
        System.out.println("block lambda (reversed): " + hosts);
        hosts.sort(byLength);
        System.out.println("expression lambda: " + hosts);

        // Target typing: identical text, two different functional interfaces
        Comparator<String> cmp = (a, b) -> a.length() - b.length();
        BiFunction<String, String, Integer> diff = (a, b) -> a.length() - b.length();
        System.out.println("as Comparator: " + cmp.compare("admin", "root"));
        System.out.println("as BiFunction: " + diff.apply("admin", "root"));

        // Zero parameters, one parameter without parentheses
        Supplier<String> banner = () -> "Authorized use only";
        java.util.function.Function<String, String> shout = s -> s.toUpperCase();
        System.out.println(shout.apply(banner.get()));

        // Capturing an effectively final local
        int maxAttempts = 3;
        java.util.function.IntPredicate locked = attempts -> attempts >= maxAttempts;
        System.out.println("2 attempts locked? " + locked.test(2) + ", 3 attempts locked? " + locked.test(3));
        // maxAttempts++;  // would not compile: the lambda captured maxAttempts

        // A common workaround for "counting inside a lambda": a one-element array
        // (fine single-threaded; use an AtomicInteger or a stream count otherwise)
        int[] failures = {0};
        List.of("ok", "fail", "ok", "fail", "fail").forEach(r -> {
            if (r.equals("fail")) failures[0]++;
        });
        System.out.println("failures counted in lambda: " + failures[0]);

        new LambdaSyntaxExample().thisDemo();
    }

    void thisDemo() {
        Runnable lambda = () -> System.out.println("lambda: this is the outer object? "
                + (this instanceof LambdaSyntaxExample) + ", this.name = " + this.name);
        Runnable anon = new Runnable() {
            @Override
            public void run() {
                System.out.println("anonymous class: this is the outer object? "
                        + ((Object) this instanceof LambdaSyntaxExample));
            }
        };
        lambda.run();
        anon.run();
    }
}

/* Output:
anonymous class: [web, vpn, cache01, db-primary]
block lambda (reversed): [db-primary, cache01, web, vpn]
expression lambda: [web, vpn, cache01, db-primary]
as Comparator: 1
as BiFunction: 1
AUTHORIZED USE ONLY
2 attempts locked? false, 3 attempts locked? true
failures counted in lambda: 3
lambda: this is the outer object? true, this.name = outer instance
anonymous class: this is the outer object? false
*/
