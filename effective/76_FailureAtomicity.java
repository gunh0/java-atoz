// Effective Java, Item 76: Strive for failure atomicity.
//
// A failed method call should leave the object in the state it was in
// before the call, so the caller can recover and keep using it. Ways to get
// there, from simplest: use immutable objects (they cannot be left
// half-updated); check parameters and state before modifying anything;
// order the computation so that whatever can fail happens before the first
// mutation; or perform the operation on a temporary copy and swap it in only
// on success. Where atomicity is too costly (e.g. after a
// ConcurrentModificationException) document the state the object is left in.
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EmptyStackException;
import java.util.List;

public class FailureAtomicity {
    static final class Stack {
        private Object[] elements = new Object[4];
        private int size;

        void push(Object e) {
            if (size == elements.length) {
                elements = Arrays.copyOf(elements, 2 * size);
            }
            elements[size++] = e;
        }

        // Not atomic: size is decremented before the failure is detected
        Object popBroken() {
            Object result = elements[--size];
            if (result == null) {
                throw new EmptyStackException();
            }
            elements[size] = null;
            return result;
        }

        // Atomic: check state first, mutate afterwards
        Object pop() {
            if (size == 0) {
                throw new EmptyStackException();
            }
            Object result = elements[--size];
            elements[size] = null;
            return result;
        }

        int size() {
            return size;
        }
    }

    public static void main(String[] args) {
        Stack s = new Stack();
        try {
            s.popBroken();
        } catch (RuntimeException e) {
            System.out.println("popBroken on empty: " + e.getClass().getSimpleName() + ", size now " + s.size());
        }

        Stack t = new Stack();
        try {
            t.pop();
        } catch (EmptyStackException e) {
            System.out.println("pop on empty: EmptyStackException, size still " + t.size());
        }
        t.push("a");
        System.out.println("still usable: pushed and popped " + t.pop());

        // Temporary copy: sort a copy, publish it only if sorting succeeded
        List<Object> mixed = new ArrayList<>(List.of("pear", "apple", "fig", 42));
        Comparator<Object> byString = Comparator.comparing(o -> (String) o); // fails on 42

        List<Object> inPlace = new ArrayList<>(mixed);
        try {
            inPlace.sort(byString);
        } catch (ClassCastException e) {
            System.out.println("in-place sort failed, list left half-sorted: " + inPlace);
        }

        List<Object> current = mixed;
        try {
            List<Object> copy = new ArrayList<>(current);
            copy.sort(byString);
            current = copy;
        } catch (ClassCastException e) {
            System.out.println("copy-then-swap failed, original kept: " + current);
        }
    }
}

/* Output:
popBroken on empty: ArrayIndexOutOfBoundsException, size now -1
pop on empty: EmptyStackException, size still 0
still usable: pushed and popped a
in-place sort failed, list left half-sorted: [apple, fig, pear, 42]
copy-then-swap failed, original kept: [pear, apple, fig, 42]
*/
