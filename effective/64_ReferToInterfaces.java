// Effective Java, Item 64: Refer to objects by their interfaces.
//
// If an appropriate interface exists, use it for parameter, return value,
// variable and field types: Set<Son> sons = new LinkedHashSet<>(), not
// LinkedHashSet<Son> sons = .... The only place the class appears is the
// constructor call, so switching implementations (for speed, ordering or
// concurrency) is a one-word change and the rest of the code keeps
// compiling. Caveat: if the code depends on a property only one class
// guarantees (LinkedHashSet's insertion order), the variable's declaration
// should say so in a comment. Use a class type only when no interface fits:
// value classes such as String and BigInteger, class-based frameworks such
// as OutputStream, or when you need extra methods (PriorityQueue.comparator).
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

public class ReferToInterfaces {
    public static void main(String[] args) {
        List<String> arrivals = List.of("carol", "alice", "bob", "alice");

        // Client code depends only on Set; only the factory changes
        report("LinkedHashSet", LinkedHashSet::new, arrivals); // insertion order
        report("TreeSet", TreeSet::new, arrivals);             // sorted order
        report("HashSet", HashSet::new, arrivals);             // no order promised: print size only

        // Declared as the class: now every caller is welded to it
        LinkedHashSet<String> welded = new LinkedHashSet<>(arrivals);
        // TreeSet<String> t = welded; // does not compile; changing the type ripples outward
        System.out.println("class-typed variable: " + welded.getClass().getSimpleName());

        // A legitimate class type: comparator() is not part of Queue
        PriorityQueue<String> queue = new PriorityQueue<>(Comparator.reverseOrder());
        queue.addAll(arrivals);
        System.out.println("PriorityQueue.comparator(): " + (queue.comparator() != null) + ", head = " + queue.peek());
    }

    static void report(String name, Supplier<Set<String>> factory, List<String> input) {
        Set<String> people = factory.get();
        people.addAll(input);
        if (people instanceof HashSet && !(people instanceof LinkedHashSet)) {
            System.out.println(name + ": " + people.size() + " unique");
        } else {
            System.out.println(name + ": " + people.size() + " unique " + people);
        }
    }
}

/* Output:
LinkedHashSet: 3 unique [carol, alice, bob]
TreeSet: 3 unique [alice, bob, carol]
HashSet: 3 unique
class-typed variable: LinkedHashSet
PriorityQueue.comparator(): true, head = carol
*/
