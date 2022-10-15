// Effective Java, Item 30: Favor generic methods.
//
// Static utility methods are good candidates for generics: declare the type
// parameter list before the return type and the compiler infers it from the
// arguments, so callers need no casts. A generic singleton factory hands out
// one shared immutable object (e.g. an identity function) typed for whatever
// the caller needs, which is safe because erasure leaves only one object.
// Recursive type bounds such as <E extends Comparable<E>> express "an E that
// can be compared with other Es", exactly what max() needs.
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

public class GenericMethods {
    public static void main(String[] args) {
        // Generic method: the element type is inferred, the result is typed
        Set<String> guys = Set.of("Tom", "Dick", "Harry");
        Set<String> stooges = Set.of("Larry", "Moe", "Curly");
        Set<String> aflCio = union(new java.util.TreeSet<>(guys), new java.util.TreeSet<>(stooges));
        System.out.println("union: " + aflCio);

        // Raw-type version: compiles, but returns a raw Set of anything
        Set<?> rawUnion = rawUnion(Set.of(1), Set.of("one"));
        System.out.println("raw union mixes types: " + rawUnion.size() + " elements");

        // Generic singleton factory: one object, many types
        UnaryOperator<String> sameString = identityFunction();
        UnaryOperator<Number> sameNumber = identityFunction();
        System.out.println("identity: " + sameString.apply("jute") + ", " + sameNumber.apply(3.0));
        System.out.println("same instance for both types: " + ((Object) sameString == sameNumber));

        // Recursive type bound
        System.out.println("max word: " + max(List.of("apple", "pear", "fig")));
        System.out.println("max number: " + max(List.of(3, 41, 7)));
    }

    static <E> Set<E> union(Set<E> s1, Set<E> s2) {
        Set<E> result = new LinkedHashSet<>(s1);
        result.addAll(s2);
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"}) // the anti-pattern, kept for the demo
    static Set rawUnion(Set s1, Set s2) {
        Set result = new LinkedHashSet(s1);
        result.addAll(s2);
        return result;
    }

    private static final UnaryOperator<Object> IDENTITY_FN = t -> t;

    // IDENTITY_FN returns its argument unmodified, so it is a valid
    // UnaryOperator<T> for every T.
    @SuppressWarnings("unchecked")
    static <T> UnaryOperator<T> identityFunction() {
        return (UnaryOperator<T>) IDENTITY_FN;
    }

    static <E extends Comparable<E>> E max(Collection<E> c) {
        if (c.isEmpty()) {
            throw new IllegalArgumentException("Empty collection");
        }
        E result = null;
        for (E e : c) {
            if (result == null || e.compareTo(result) > 0) {
                result = Objects.requireNonNull(e);
            }
        }
        return result;
    }
}

/* Output:
union: [Dick, Harry, Tom, Curly, Larry, Moe]
raw union mixes types: 2 elements
identity: jute, 3.0
same instance for both types: true
max word: pear
max number: 41
*/
