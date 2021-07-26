// Effective Java, Item 7: Eliminate obsolete object references.
//
// Garbage collection does not make memory leaks impossible. Whenever a class
// manages its own memory (an array-backed stack, a cache, a listener list),
// elements it no longer considers live may still be referenced from its
// internal storage, so the GC can never reclaim them nor anything they point
// to. The fix is to null out the reference as soon as it becomes obsolete
// (which also turns accidental reuse into a fast NullPointerException).
// Do this only inside classes that manage their own memory; elsewhere let
// variables simply fall out of scope.
import java.util.Arrays;
import java.util.EmptyStackException;

public class ObsoleteReferences {
    public static void main(String[] args) {
        LeakyStack leaky = new LeakyStack();
        FixedStack fixed = new FixedStack();
        for (int i = 0; i < 10; i++) {
            leaky.push(new byte[1024 * 1024]); // 1 MiB payload
            fixed.push(new byte[1024 * 1024]);
        }
        for (int i = 0; i < 10; i++) {
            leaky.pop();
            fixed.pop();
        }

        // Both stacks are logically empty...
        System.out.println("leaky size: " + leaky.size() + ", fixed size: " + fixed.size());
        // ...but the leaky one still pins 10 MiB that the GC cannot touch
        System.out.println("leaky stack still references: " + leaky.retainedSlots() + " arrays");
        System.out.println("fixed stack still references: " + fixed.retainedSlots() + " arrays");
    }
}

// Anti-pattern: pop() decrements size but leaves the reference in the array
class LeakyStack {
    private static final int DEFAULT_INITIAL_CAPACITY = 16;
    protected Object[] elements = new Object[DEFAULT_INITIAL_CAPACITY];
    protected int size = 0;

    void push(Object e) {
        ensureCapacity();
        elements[size++] = e;
    }

    Object pop() {
        if (size == 0) {
            throw new EmptyStackException();
        }
        return elements[--size]; // the slot keeps an obsolete reference
    }

    int size() {
        return size;
    }

    // Diagnostic only: how many slots beyond `size` still hold an object
    long retainedSlots() {
        return Arrays.stream(elements, size, elements.length).filter(e -> e != null).count();
    }

    private void ensureCapacity() {
        if (elements.length == size) {
            elements = Arrays.copyOf(elements, 2 * size + 1);
        }
    }
}

// Recommended: clear the slot as soon as it leaves the logical stack
class FixedStack extends LeakyStack {
    @Override
    Object pop() {
        if (size == 0) {
            throw new EmptyStackException();
        }
        Object result = elements[--size];
        elements[size] = null; // eliminate obsolete reference
        return result;
    }
}

/* Output:
leaky size: 0, fixed size: 0
leaky stack still references: 10 arrays
fixed stack still references: 0 arrays
*/
