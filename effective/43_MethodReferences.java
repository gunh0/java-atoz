// Effective Java, Item 43: Prefer method references to lambdas.
//
// A method reference is usually an even shorter function object than a
// lambda: map.merge(key, 1, Integer::sum) says what it does without inventing
// parameter names. There are five kinds: static (Integer::parseInt), bound
// (Instant.now()::isAfter, receiver fixed now), unbound (String::toLowerCase,
// receiver passed later), class constructor (TreeMap::new) and array
// constructor (int[]::new). The rule is not absolute: when the method lives in
// the same class with a long name, or when x -> x reads better than
// Function.identity(), keep the lambda. Pick whichever is shorter and clearer.
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class MethodReferences {
    public static void main(String[] args) {
        List<String> words = List.of("to", "be", "or", "not", "to", "be");

        // Lambda: the parameter names add nothing
        Map<String, Integer> lambdaCounts = new TreeMap<>();
        for (String w : words) {
            lambdaCounts.merge(w, 1, (count, incr) -> count + incr);
        }
        // Method reference: says "sum" directly
        Map<String, Integer> refCounts = new TreeMap<>();
        for (String w : words) {
            refCounts.merge(w, 1, Integer::sum);
        }
        System.out.println("lambda:     " + lambdaCounts);
        System.out.println("method ref: " + refCounts);

        // The five kinds, each next to its lambda equivalent
        Function<String, Integer> staticRef = Integer::parseInt;        // str -> Integer.parseInt(str)
        String prefix = "se";
        Predicate<String> boundRef = prefix::equals;                     // s -> prefix.equals(s)
        Function<String, String> unboundRef = String::toUpperCase;       // s -> s.toUpperCase()
        Supplier<TreeMap<String, Integer>> ctorRef = TreeMap::new;       // () -> new TreeMap<>()
        IntFunction<int[]> arrayCtorRef = int[]::new;                    // len -> new int[len]

        System.out.println("static:      " + staticRef.apply("42"));
        System.out.println("bound:       " + boundRef.test("se") + " " + boundRef.test("cs"));
        System.out.println("unbound:     " + unboundRef.apply("hello"));
        System.out.println("constructor: " + ctorRef.get().getClass().getSimpleName());
        System.out.println("array ctor:  " + Arrays.toString(arrayCtorRef.apply(3)));

        // Unbound references take the receiver as the first argument
        BiFunction<String, String, Boolean> startsWith = String::startsWith;
        System.out.println("unbound, two args: " + startsWith.apply("method", "meth"));

        // Sometimes the lambda is clearer than the reference
        Function<String, String> identityRef = Function.identity();
        Function<String, String> identityLambda = x -> x;
        System.out.println("identity both ways: " + identityRef.apply("same") + " " + identityLambda.apply("same"));
    }
}

/* Output:
lambda:     {be=2, not=1, or=1, to=2}
method ref: {be=2, not=1, or=1, to=2}
static:      42
bound:       true false
unbound:     HELLO
constructor: TreeMap
array ctor:  [0, 0, 0]
unbound, two args: true
identity both ways: same same
*/
