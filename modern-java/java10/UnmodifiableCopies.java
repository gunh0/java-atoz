// Java 10 — List.copyOf, Collectors.toUnmodifiableList, Optional.orElseThrow()
// (API additions, no JEP)
//
// Collections.unmodifiableList() returns a read-only VIEW: whoever still
// holds the original list can change it, and the "unmodifiable" list
// changes with it. A defensive copy needed new ArrayList<>(...) wrapped in
// unmodifiableList(). Java 10 added List/Set/Map.copyOf, which return a
// real immutable snapshot (and skip the copy when the argument is already
// one), plus Collectors.toUnmodifiableList/Set/Map for streams. The same
// release added Optional.orElseThrow() with no argument, the preferred
// spelling of get(): the name says that it can throw.
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UnmodifiableCopies {
    public static void main(String[] args) {
        List<String> source = new ArrayList<>(List.of("read", "write"));

        // Before: a view, not a copy
        List<String> view = Collections.unmodifiableList(source);
        // Before: the correct but noisy defensive copy
        List<String> oldCopy = Collections.unmodifiableList(new ArrayList<>(source));
        // After: one call, immutable snapshot
        List<String> copy = List.copyOf(source);

        source.add("admin"); // someone mutates the original afterwards
        System.out.println("unmodifiableList view: " + view);
        System.out.println("old defensive copy:    " + oldCopy);
        System.out.println("List.copyOf:           " + copy);

        try {
            copy.add("admin");
        } catch (UnsupportedOperationException e) {
            System.out.println("copy.add: UnsupportedOperationException");
        }

        // copyOf of an already-immutable list returns the same instance
        System.out.println("copyOf(copy) == copy: " + (List.copyOf(copy) == copy));
        // ... and, like List.of, it rejects nulls
        try {
            List.copyOf(java.util.Arrays.asList("x", null));
        } catch (NullPointerException e) {
            System.out.println("copyOf with a null element: NullPointerException");
        }

        // Set.copyOf and Map.copyOf work the same way (duplicates in Set.copyOf are dropped)
        Set<String> roles = Set.copyOf(List.of("user", "user", "auditor"));
        Map<String, Integer> limits = Map.copyOf(Map.of("user", 10));
        System.out.println("Set.copyOf size: " + roles.size() + ", Map.copyOf: " + limits);

        // Collectors.toUnmodifiableList: the stream result cannot be modified later
        List<Integer> evens = Stream.of(1, 2, 3, 4, 5, 6)
                .filter(n -> n % 2 == 0)
                .collect(Collectors.toUnmodifiableList());
        try {
            evens.set(0, 99);
        } catch (UnsupportedOperationException e) {
            System.out.println("toUnmodifiableList " + evens + ": set() rejected");
        }
        Map<Integer, String> byLength = Stream.of("go", "java", "kotlin")
                .collect(Collectors.toUnmodifiableMap(String::length, s -> s));
        System.out.println("toUnmodifiableMap(4): " + byLength.get(4));

        // Optional.orElseThrow(): same as get(), but the name warns the reader
        Optional<String> admin = copy.stream().filter(r -> r.equals("admin")).findFirst();
        Optional<String> write = copy.stream().filter(r -> r.startsWith("w")).findFirst();
        System.out.println("orElseThrow on a present value: " + write.orElseThrow());
        try {
            admin.orElseThrow();
        } catch (java.util.NoSuchElementException e) {
            System.out.println("orElseThrow on empty: NoSuchElementException");
        }
    }
}

/* Output:
unmodifiableList view: [read, write, admin]
old defensive copy:    [read, write]
List.copyOf:           [read, write]
copy.add: UnsupportedOperationException
copyOf(copy) == copy: true
copyOf with a null element: NullPointerException
Set.copyOf size: 2, Map.copyOf: {user=10}
toUnmodifiableList [2, 4, 6]: set() rejected
toUnmodifiableMap(4): java
orElseThrow on a present value: write
orElseThrow on empty: NoSuchElementException
*/
