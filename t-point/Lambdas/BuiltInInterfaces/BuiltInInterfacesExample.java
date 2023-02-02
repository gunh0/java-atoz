// java.util.function ships about 40 functional interfaces so you rarely need
// your own. They come from a handful of shapes:
//   Supplier<T>       () -> T            Consumer<T>      T -> void
//   Function<T, R>    T -> R             Predicate<T>     T -> boolean
//   UnaryOperator<T>  T -> T             BinaryOperator<T> (T, T) -> T
//   BiFunction / BiConsumer / BiPredicate: the same with two arguments
// plus primitive specializations (IntPredicate, ToIntFunction, IntBinaryOperator,
// LongSupplier, ...) that avoid boxing an int into an Integer on every call.
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;

public class BuiltInInterfacesExample {
    record Request(String method, String path, int status, int bytes) {}

    public static void main(String[] args) {
        List<Request> log = List.of(
                new Request("GET", "/index.html", 200, 5120),
                new Request("POST", "/login", 401, 230),
                new Request("GET", "/admin", 403, 180),
                new Request("GET", "/app.js", 200, 20480));

        // Predicate: a yes/no question about one value
        Predicate<Request> isError = r -> r.status() >= 400;
        // Function: transform a value into another
        Function<Request, String> summary = r -> r.method() + " " + r.path() + " " + r.status();
        // Consumer: do something with a value, return nothing
        Consumer<String> printer = s -> System.out.println("  " + s);

        System.out.println("errors:");
        for (Request r : log) {
            if (isError.test(r)) printer.accept(summary.apply(r));
        }

        // Supplier: produce a value on demand (here a fresh, mutable list)
        Supplier<List<String>> newBucket = ArrayList::new;
        List<String> paths = newBucket.get();
        log.forEach(r -> paths.add(r.path()));
        System.out.println("paths: " + paths);

        // UnaryOperator and BinaryOperator: same type in and out
        UnaryOperator<String> redact = p -> p.startsWith("/admin") ? "/[redacted]" : p;
        BinaryOperator<Integer> larger = (a, b) -> a >= b ? a : b;
        System.out.println("redact(/admin) = " + redact.apply("/admin"));
        System.out.println("larger(230, 180) = " + larger.apply(230, 180));

        // Two-argument variants
        BiFunction<String, Integer, String> tag = (path, status) -> path + "#" + status;
        BiConsumer<String, Integer> report = (k, v) -> System.out.println("  " + k + " = " + v);
        System.out.println("tag = " + tag.apply("/login", 401));
        report.accept("requests", log.size());

        // Primitive specializations: no Integer boxing per call
        ToIntFunction<Request> size = Request::bytes;
        IntBinaryOperator add = (a, b) -> a + b;
        IntPredicate isLarge = n -> n > 10_000;
        int total = 0;
        int large = 0;
        for (Request r : log) {
            int b = size.applyAsInt(r);
            total = add.applyAsInt(total, b);
            if (isLarge.test(b)) large++;
        }
        System.out.println("total bytes = " + total + ", large responses = " + large);
    }
}

/* Output:
errors:
  POST /login 401
  GET /admin 403
paths: [/index.html, /login, /admin, /app.js]
redact(/admin) = /[redacted]
larger(230, 180) = 230
tag = /login#401
  requests = 4
total bytes = 26010, large responses = 1
*/
