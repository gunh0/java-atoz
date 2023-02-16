// A method reference is a lambda that only calls an existing method, written
// with `::`. There are four kinds:
//   Class::staticMethod       Integer::parseInt      s -> Integer.parseInt(s)
//   object::instanceMethod    log::add               s -> log.add(s)
//   Class::instanceMethod     String::length         s -> s.length()
//                             (the first argument becomes the receiver)
//   Class::new                ArrayList::new         () -> new ArrayList<>()
// A bound reference (object::method) evaluates `object` once, when the
// reference is created, not every time it is called.
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public class MethodReferencesExample {
    record Port(int number) {
        static boolean isPrivileged(Port p) {
            return p.number() < 1024;
        }
    }

    static StringBuilder current = new StringBuilder("first");

    public static void main(String[] args) {
        List<String> raw = List.of("22", "443", "8080", "3306");

        // 1. Static method
        Function<String, Integer> parse = Integer::parseInt;
        List<Integer> numbers = raw.stream().map(parse).toList();
        System.out.println("parsed: " + numbers);

        // 4. Constructor (a record's canonical constructor here)
        List<Port> ports = numbers.stream().map(Port::new).toList();
        System.out.println("ports: " + ports);

        // 1 again, on our own static method, used as a Predicate
        System.out.println("privileged: " + ports.stream().filter(Port::isPrivileged).toList());

        // 3. Instance method of an arbitrary object: the receiver is the argument
        Function<String, Integer> length = String::length;
        BiFunction<String, String, Boolean> startsWith = String::startsWith;
        System.out.println("length(\"8080\") = " + length.apply("8080"));
        System.out.println("startsWith(\"/admin/users\", \"/admin\") = " + startsWith.apply("/admin/users", "/admin"));

        // 2. Instance method of a particular (bound) object
        List<String> audit = new ArrayList<>();
        Consumer<String> recorder = audit::add;
        raw.forEach(recorder);
        System.out.println("audit: " + audit);
        raw.forEach(System.out::println); // System.out is evaluated once, here

        // Constructor references for collections and arrays
        Supplier<List<String>> listFactory = ArrayList::new;
        IntFunction<String[]> arrayFactory = String[]::new;
        List<String> fresh = listFactory.get();
        fresh.add("new");
        String[] asArray = raw.stream().toArray(arrayFactory);
        System.out.println("fresh = " + fresh + ", array length = " + asArray.length);

        // The receiver of a bound reference is evaluated once, at creation;
        // a lambda re-reads the field on every call
        Supplier<String> bound = current::toString;
        Supplier<String> lambda = () -> current.toString();
        current = new StringBuilder("second");
        System.out.println("bound = " + bound.get() + ", lambda = " + lambda.get());
    }
}

/* Output:
parsed: [22, 443, 8080, 3306]
ports: [Port[number=22], Port[number=443], Port[number=8080], Port[number=3306]]
privileged: [Port[number=22], Port[number=443]]
length("8080") = 4
startsWith("/admin/users", "/admin") = true
audit: [22, 443, 8080, 3306]
22
443
8080
3306
fresh = [new], array length = 4
bound = first, lambda = second
*/
