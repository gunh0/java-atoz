// Effective Java, Item 47: Prefer Collection to Stream as a return type.
//
// Some callers want to iterate a result with for-each, others want a stream.
// Stream has an iterator() method but does not implement Iterable, so a
// method that returns Stream forces loop users into an adapter, and one that
// returns Iterable forces stream users into StreamSupport. Collection is a
// subtype of Iterable and has stream(), so it serves both: return a standard
// collection when the result fits in memory, or a custom AbstractList /
// AbstractCollection when it is large but has a compact representation
// (such as the power set below, which never stores its 2^n elements). Return
// a Stream only when a collection would be impractical, e.g. no size().
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class CollectionOverStreamReturn {
    public static void main(String[] args) {
        // A Stream-returning method: for-each does not compile without an adapter
        // for (String s : namesAsStream()) { }   // error: for-each not applicable to Stream
        for (String s : iterableOf(namesAsStream())) {
            System.out.println("adapted stream: " + s);
        }

        // An Iterable-returning method: streaming needs StreamSupport
        Iterable<String> iterable = List.of("x", "y");
        System.out.println("adapted iterable: " + streamOf(iterable).map(String::toUpperCase).toList());

        // Returning Collection: both styles work directly
        Collection<Set<Character>> subsets = PowerSet.of(new LinkedHashSet<>(List.of('a', 'b', 'c')));
        System.out.println("power set size: " + subsets.size());
        for (Set<Character> s : subsets) {
            System.out.print(s + " ");
        }
        System.out.println();
        System.out.println("subsets of size 2: "
                + subsets.stream().filter(s -> s.size() == 2).collect(Collectors.toList()));

        // The custom collection stores only the base set, not 2^20 subsets
        Set<Integer> twenty = new HashSet<>();
        for (int i = 0; i < 20; i++) twenty.add(i);
        System.out.println("power set of 20 elements, size: " + PowerSet.of(twenty).size());
    }

    static Stream<String> namesAsStream() {
        return Stream.of("ann", "bob");
    }

    // Stream -> Iterable: Stream::iterator has the right shape for Iterable
    static <E> Iterable<E> iterableOf(Stream<E> stream) {
        return stream::iterator;
    }

    // Iterable -> Stream
    static <E> Stream<E> streamOf(Iterable<E> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }
}

class PowerSet {
    // Each index is a bit vector: bit i set means "element i is in the subset"
    static <E> Collection<Set<E>> of(Set<E> s) {
        List<E> src = new ArrayList<>(s);
        if (src.size() > 30) {
            throw new IllegalArgumentException("set too big: " + s);
        }
        return new AbstractList<>() {
            @Override
            public int size() {
                return 1 << src.size();
            }

            @Override
            public boolean contains(Object o) {
                return o instanceof Set<?> set && src.containsAll(set);
            }

            @Override
            public Set<E> get(int index) {
                Set<E> result = new LinkedHashSet<>();
                for (int i = 0; index != 0; i++, index >>= 1) {
                    if ((index & 1) == 1) {
                        result.add(src.get(i));
                    }
                }
                return result;
            }
        };
    }
}

/* Output:
adapted stream: ann
adapted stream: bob
adapted iterable: [X, Y]
power set size: 8
[] [a] [b] [a, b] [c] [a, c] [b, c] [a, b, c] 
subsets of size 2: [[a, b], [a, c], [b, c]]
power set of 20 elements, size: 1048576
*/
