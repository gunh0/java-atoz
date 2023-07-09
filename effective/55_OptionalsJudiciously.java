// Effective Java, Item 55: Return optionals judiciously.
//
// When a method may have no result, the old choices were to throw an
// exception (expensive, and not exceptional) or return null (callers forget
// to check). Optional<T> makes the possible absence part of the type and
// gives the caller a fluent choice: orElse, orElseGet, orElseThrow, map,
// filter, ifPresent... Rules of thumb: never return null from an
// Optional-returning method; do not wrap containers (return an empty list,
// not Optional<List>); use OptionalInt/Long/Double instead of
// Optional<Integer>; and do not use Optional as a map value, collection
// element or (usually) a field. Optional allocates, so skip it on hot paths.
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;

public class OptionalsJudiciously {
    public static void main(String[] args) {
        List<String> words = List.of("kiwi", "banana", "fig");
        List<String> none = List.of();

        // Throwing on empty input: the caller must catch
        try {
            maxOrThrow(none);
        } catch (IllegalArgumentException e) {
            System.out.println("maxOrThrow(empty): " + e.getMessage());
        }

        // Optional: the caller decides
        System.out.println("max(words): " + max(words));
        System.out.println("max(empty): " + max(none));
        System.out.println("orElse:     " + max(none).orElse("no words"));
        System.out.println("orElseGet:  " + max(none).orElseGet(() -> "computed default"));
        System.out.println("map:        " + max(words).map(String::length).orElse(0));
        System.out.println("filter:     " + max(words).filter(w -> w.startsWith("b")).isPresent());
        try {
            max(none).orElseThrow(() -> new IllegalStateException("nothing to pick"));
        } catch (IllegalStateException e) {
            System.out.println("orElseThrow: " + e.getMessage());
        }

        // The library returns Optional too
        System.out.println("stream max: " + words.stream().max(String::compareTo).get());

        // Primitive optional, no boxing
        OptionalInt firstEven = IntStream.of(3, 5, 8, 10).filter(n -> n % 2 == 0).findFirst();
        System.out.println("OptionalInt: " + firstEven.getAsInt());

        // Flatten a stream of optionals
        List<String> present = List.of(max(words), max(none), Optional.of("x")).stream()
                .flatMap(Optional::stream).toList();
        System.out.println("present values: " + present);
    }

    static <E extends Comparable<E>> E maxOrThrow(Collection<E> c) {
        if (c.isEmpty()) {
            throw new IllegalArgumentException("empty collection");
        }
        E result = null;
        for (E e : c) {
            if (result == null || e.compareTo(result) > 0) {
                result = Objects.requireNonNull(e);
            }
        }
        return result;
    }

    static <E extends Comparable<E>> Optional<E> max(Collection<E> c) {
        E result = null;
        for (E e : c) {
            if (result == null || e.compareTo(result) > 0) {
                result = Objects.requireNonNull(e);
            }
        }
        return Optional.ofNullable(result); // never "return null" here
    }
}

/* Output:
maxOrThrow(empty): empty collection
max(words): Optional[kiwi]
max(empty): Optional.empty
orElse:     no words
orElseGet:  computed default
map:        4
filter:     false
orElseThrow: nothing to pick
stream max: kiwi
OptionalInt: 8
present values: [kiwi, x]
*/
