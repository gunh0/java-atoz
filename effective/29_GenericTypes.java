// Effective Java, Item 29: Favor generic types.
//
// A container written in terms of Object forces every client to cast what it
// takes out, and the casts can fail at run time. Making the type generic
// moves that check to compile time. Because you cannot create an array of a
// type parameter (new E[n]), there are two standard implementations: create
// an Object[] and cast it to E[] once in the constructor (safe because the
// array never escapes), or keep an Object[] field and cast each element on
// the way out. Both need a single, documented @SuppressWarnings. A type
// parameter can also be bounded, e.g. <E extends Comparable<E>>.
import java.util.Arrays;
import java.util.EmptyStackException;

public class GenericTypes {
    public static void main(String[] args) {
        // Object-based stack: casts in client code, failures at run time
        ObjectStack objects = new ObjectStack();
        objects.push("hello");
        objects.push(42); // nothing stops this
        try {
            while (!objects.isEmpty()) {
                String s = (String) objects.pop();
                System.out.println("object stack: " + s.toUpperCase());
            }
        } catch (ClassCastException e) {
            System.out.println("object stack: ClassCastException in client code");
        }

        // Generic stack: no casts, wrong pushes do not compile
        Stack<String> strings = new Stack<>();
        for (String s : new String[] {"to", "be", "or", "not"}) {
            strings.push(s);
        }
        // strings.push(42); // does not compile
        StringBuilder sb = new StringBuilder();
        while (!strings.isEmpty()) {
            sb.append(strings.pop().toUpperCase()).append(' ');
        }
        System.out.println("generic stack: " + sb.toString().trim());

        // Bounded type parameter: only comparable elements, with a max()
        SortedBag<Integer> bag = new SortedBag<>();
        bag.add(5);
        bag.add(17);
        bag.add(3);
        System.out.println("bounded bag max: " + bag.max());
    }
}

class ObjectStack {
    private Object[] elements = new Object[16];
    private int size;

    void push(Object e) {
        elements[size++] = e;
    }

    Object pop() {
        if (size == 0) {
            throw new EmptyStackException();
        }
        Object result = elements[--size];
        elements[size] = null;
        return result;
    }

    boolean isEmpty() {
        return size == 0;
    }
}

class Stack<E> {
    private E[] elements;
    private int size;

    // The array contains only E instances from push(E) and never leaves this
    // class, so the cast is safe even though its runtime type is Object[].
    @SuppressWarnings("unchecked")
    Stack() {
        elements = (E[]) new Object[16];
    }

    void push(E e) {
        if (size == elements.length) {
            elements = Arrays.copyOf(elements, 2 * size + 1);
        }
        elements[size++] = e;
    }

    E pop() {
        if (size == 0) {
            throw new EmptyStackException();
        }
        E result = elements[--size];
        elements[size] = null;
        return result;
    }

    boolean isEmpty() {
        return size == 0;
    }
}

class SortedBag<E extends Comparable<E>> {
    private final java.util.List<E> items = new java.util.ArrayList<>();

    void add(E e) {
        items.add(e);
    }

    E max() {
        E best = items.get(0);
        for (E e : items) {
            if (e.compareTo(best) > 0) {
                best = e;
            }
        }
        return best;
    }
}

/* Output:
object stack: ClassCastException in client code
generic stack: NOT OR BE TO
bounded bag max: 17
*/
