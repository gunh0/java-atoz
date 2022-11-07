// java.util.Collections is a toolbox of static methods for collections:
// sorting and searching, reordering, counting, and wrappers that change how a
// collection may be used (unmodifiable, synchronized, checked).
//  - binarySearch only works on a list sorted by the same ordering
//  - shuffle(list, new Random(seed)) is reproducible, which is useful in tests
//  - unmodifiableList is a read-only VIEW: changes to the original show through.
//    List.copyOf (Java 10) makes an independent immutable copy instead
//  - synchronizedList makes each call thread-safe, but iteration still needs
//    an explicit synchronized (list) block
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class CollectionsUtilityExample {
    public static void main(String[] args) {
        List<Integer> latencies = new ArrayList<>(List.of(120, 45, 300, 45, 80, 210));

        System.out.println("max=" + Collections.max(latencies) + ", min=" + Collections.min(latencies));
        System.out.println("frequency(45) = " + Collections.frequency(latencies, 45));

        Collections.sort(latencies);
        System.out.println("sort: " + latencies);
        System.out.println("binarySearch(210) = index " + Collections.binarySearch(latencies, 210));
        // Not found: returns -(insertionPoint) - 1
        System.out.println("binarySearch(100) = " + Collections.binarySearch(latencies, 100));

        Collections.reverse(latencies);
        System.out.println("reverse: " + latencies);
        Collections.sort(latencies, Collections.reverseOrder());
        System.out.println("sort(reverseOrder()): " + latencies);

        List<String> deck = new ArrayList<>(List.of("A", "B", "C", "D", "E"));
        Collections.swap(deck, 0, 4);
        System.out.println("swap(0, 4): " + deck);
        Collections.rotate(deck, 2);
        System.out.println("rotate(2): " + deck);
        Collections.shuffle(deck, new Random(42)); // same seed, same order every run
        List<String> again = new ArrayList<>(List.of("E", "B", "C", "D", "A"));
        Collections.rotate(again, 2);
        Collections.shuffle(again, new Random(42));
        System.out.println("shuffle(seed 42): " + deck + ", reproducible: " + deck.equals(again));

        // Factories for small fixed lists
        System.out.println("nCopies(3, \"-\"): " + Collections.nCopies(3, "-"));
        System.out.println("emptyList(): " + Collections.emptyList());
        List<String> filled = new ArrayList<>(Collections.nCopies(3, "?"));
        Collections.fill(filled, "x");
        System.out.println("fill: " + filled);

        // unmodifiableList is a view; List.copyOf is a snapshot
        List<String> roles = new ArrayList<>(List.of("reader"));
        List<String> view = Collections.unmodifiableList(roles);
        List<String> snapshot = List.copyOf(roles);
        roles.add("admin");
        System.out.println("view sees: " + view + ", snapshot sees: " + snapshot);
        try {
            view.add("root");
        } catch (UnsupportedOperationException e) {
            System.out.println("view.add(): UnsupportedOperationException");
        }

        // synchronizedList: guard iteration manually
        List<String> shared = Collections.synchronizedList(new ArrayList<>(List.of("a", "b")));
        synchronized (shared) {
            for (String s : shared) {
                System.out.println("iterating under lock: " + s);
            }
        }
    }
}

/* Output:
max=300, min=45
frequency(45) = 2
sort: [45, 45, 80, 120, 210, 300]
binarySearch(210) = index 4
binarySearch(100) = -4
reverse: [300, 210, 120, 80, 45, 45]
sort(reverseOrder()): [300, 210, 120, 80, 45, 45]
swap(0, 4): [E, B, C, D, A]
rotate(2): [D, A, E, B, C]
shuffle(seed 42): [A, E, B, C, D], reproducible: true
nCopies(3, "-"): [-, -, -]
emptyList(): []
fill: [x, x, x]
view sees: [reader, admin], snapshot sees: [reader]
view.add(): UnsupportedOperationException
iterating under lock: a
iterating under lock: b
*/
