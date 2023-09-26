// JEP 431 — Sequenced Collections (Java 21)
//
// List, Deque, LinkedHashSet and SortedSet all have a defined encounter
// order, but before Java 21 there was no common type for "a collection
// with a first and a last element", and every type spelled the operations
// differently: list.get(0) / list.get(list.size() - 1), deque.getFirst(),
// sortedSet.first(), and for LinkedHashSet no way at all to get the last
// element short of iterating. Reverse iteration was just as uneven.
// Java 21 adds the SequencedCollection interface with getFirst/getLast,
// addFirst/addLast, removeFirst/removeLast and reversed(), a reverse-ordered
// VIEW, retrofitted onto List, Deque, LinkedHashSet and SortedSet (plus
// SequencedSet; maps are covered by SequencedMap).
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedCollection;
import java.util.SequencedSet;
import java.util.TreeSet;

public class SequencedCollections {
    public static void main(String[] args) {
        List<String> list = new ArrayList<>(List.of("b", "c"));
        Deque<String> deque = new ArrayDeque<>(List.of("b", "c"));
        LinkedHashSet<String> linkedSet = new LinkedHashSet<>(List.of("b", "c"));
        TreeSet<String> sorted = new TreeSet<>(List.of("b", "c"));

        // Before: four different spellings of "last element"
        String lastOfLinkedSet = null;
        for (String s : linkedSet) {
            lastOfLinkedSet = s; // the only way: walk the whole set
        }
        System.out.println("before: " + list.get(list.size() - 1) + " " + deque.peekLast() + " "
                + lastOfLinkedSet + " " + sorted.last());

        // After: one interface, one spelling
        List<SequencedCollection<String>> all = List.of(list, deque, linkedSet, sorted);
        for (SequencedCollection<String> c : all) {
            System.out.printf("%-13s first=%s last=%s reversed=%s%n",
                    c.getClass().getSimpleName(), c.getFirst(), c.getLast(), c.reversed());
        }

        // addFirst / addLast: LinkedHashSet can now move an element to either end
        linkedSet.addFirst("a");
        linkedSet.addLast("b");          // already present: moved to the end
        System.out.println("LinkedHashSet after addFirst(a), addLast(b): " + linkedSet);
        list.addFirst("a");              // instead of list.add(0, "a")
        System.out.println("List after addFirst(a): " + list + ", removeLast() -> " + list.removeLast());

        // A sorted set decides the position itself, so positional adds are refused
        try {
            sorted.addFirst("z");
        } catch (UnsupportedOperationException e) {
            System.out.println("TreeSet.addFirst: UnsupportedOperationException");
        }

        // reversed() is a live view, not a copy
        SequencedSet<String> backwards = linkedSet.reversed();
        linkedSet.add("d");
        System.out.println("reversed view sees later additions: " + backwards);
        Iterator<String> it = list.reversed().iterator();
        System.out.println("reverse iteration over a List: " + it.next() + ", " + it.next());

        // Generic code can now ask for "the last element" of any ordered collection
        System.out.println("newest: " + newest(list) + ", " + newest(deque) + ", " + newest(sorted));

        // Unmodifiable wrapper for the new type
        SequencedCollection<String> readOnly = Collections.unmodifiableSequencedCollection(deque);
        try {
            readOnly.removeFirst();
        } catch (UnsupportedOperationException e) {
            System.out.println("unmodifiableSequencedCollection.removeFirst: UnsupportedOperationException");
        }
    }

    static <E> E newest(SequencedCollection<E> events) {
        return events.getLast();
    }
}

/* Output:
before: c c c c
ArrayList     first=b last=c reversed=[c, b]
ArrayDeque    first=b last=c reversed=[c, b]
LinkedHashSet first=b last=c reversed=[c, b]
TreeSet       first=b last=c reversed=[c, b]
LinkedHashSet after addFirst(a), addLast(b): [a, c, b]
List after addFirst(a): [a, b, c], removeLast() -> c
TreeSet.addFirst: UnsupportedOperationException
reversed view sees later additions: [d, b, c, a]
reverse iteration over a List: b, a
newest: b, c, c
unmodifiableSequencedCollection.removeFirst: UnsupportedOperationException
*/
