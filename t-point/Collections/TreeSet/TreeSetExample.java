// The three general-purpose Set implementations differ only in ORDER and cost:
//  - HashSet:       no defined order, O(1) add/contains
//  - LinkedHashSet: insertion order (a linked list threads through the table)
//  - TreeSet:       sorted order (a red-black tree), O(log n) operations
// TreeSet is also a NavigableSet: it answers range questions such as "the
// closest element below x" (floor/lower), "above x" (ceiling/higher), and
// returns live views of a range (headSet, tailSet, subSet).
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

public class TreeSetExample {
    public static void main(String[] args) {
        List<String> arrivals = List.of("delta", "alpha", "charlie", "alpha", "bravo");

        Set<String> byArrival = new LinkedHashSet<>(arrivals);
        Set<String> sorted = new TreeSet<>(arrivals);
        System.out.println("LinkedHashSet (insertion order): " + byArrival);
        System.out.println("TreeSet (sorted order):          " + sorted);

        // LinkedHashSet: re-adding an existing element does not move it
        byArrival.add("delta");
        System.out.println("after re-adding delta:           " + byArrival);

        // NavigableSet: release versions that a deployment can choose from
        NavigableSet<Integer> versions = new TreeSet<>(List.of(10, 12, 15, 17, 21));
        System.out.println("versions: " + versions);
        System.out.println("first=" + versions.first() + ", last=" + versions.last());
        System.out.println("floor(16)   = " + versions.floor(16) + "   (<= 16)");
        System.out.println("lower(15)   = " + versions.lower(15) + "   (< 15)");
        System.out.println("ceiling(16) = " + versions.ceiling(16) + "   (>= 16)");
        System.out.println("higher(21)  = " + versions.higher(21) + " (> 21)");

        // Range views are backed by the set
        System.out.println("headSet(15)        = " + versions.headSet(15));
        System.out.println("headSet(15, true)  = " + versions.headSet(15, true));
        System.out.println("tailSet(15)        = " + versions.tailSet(15));
        System.out.println("subSet(11, 18)     = " + versions.subSet(11, 18));
        System.out.println("descendingSet()    = " + versions.descendingSet());

        NavigableSet<Integer> modern = versions.tailSet(15, true);
        versions.add(19); // visible through the view
        System.out.println("view after add(19) = " + modern);

        // pollFirst removes and returns the smallest element
        System.out.println("pollFirst() = " + versions.pollFirst() + ", left " + versions);

        // TreeSet uses compareTo, not equals: a case-insensitive order merges "Bob" and "bob"
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(List.of("bob", "Alice", "Bob", "carol"));
        System.out.println("case-insensitive TreeSet: " + names);

        // TreeSet rejects null (it would have to compare it)
        try {
            new TreeSet<String>().add(null);
        } catch (NullPointerException e) {
            System.out.println("TreeSet.add(null): NullPointerException");
        }
    }
}

/* Output:
LinkedHashSet (insertion order): [delta, alpha, charlie, bravo]
TreeSet (sorted order):          [alpha, bravo, charlie, delta]
after re-adding delta:           [delta, alpha, charlie, bravo]
versions: [10, 12, 15, 17, 21]
first=10, last=21
floor(16)   = 15   (<= 16)
lower(15)   = 12   (< 15)
ceiling(16) = 17   (>= 16)
higher(21)  = null (> 21)
headSet(15)        = [10, 12]
headSet(15, true)  = [10, 12, 15]
tailSet(15)        = [15, 17, 21]
subSet(11, 18)     = [12, 15, 17]
descendingSet()    = [21, 17, 15, 12, 10]
view after add(19) = [15, 17, 19, 21]
pollFirst() = 10, left [12, 15, 17, 19, 21]
case-insensitive TreeSet: [Alice, bob, carol]
TreeSet.add(null): NullPointerException
*/
