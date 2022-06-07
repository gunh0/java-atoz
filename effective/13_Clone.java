// Effective Java, Item 13: Override clone judiciously.
//
// Cloneable has no methods. It only changes how the protected Object.clone
// behaves: instead of throwing CloneNotSupportedException it makes a
// field-by-field copy. That copy is SHALLOW, so mutable internals such as an
// array end up shared by the original and the clone, and changing one quietly
// corrupts the other. If you must support clone, return the class's own type,
// call super.clone(), then deep-copy every mutable component (which also means
// those fields cannot be final). For new classes a copy constructor or a copy
// factory is simpler: no cast, no checked exception, works with final fields.
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

public class CloneDemo {
    public static void main(String[] args) {
        // Shallow clone: the elements array is shared
        ShallowStack shallow = new ShallowStack();
        shallow.push("a");
        shallow.push("b");
        ShallowStack shallowCopy = shallow.clone();
        shallowCopy.pop();
        shallowCopy.push("X");
        System.out.println("shallow: original pops " + shallow.pop() + " (expected b)");

        // Deep clone: the array is copied as well
        DeepStack deep = new DeepStack();
        deep.push("a");
        deep.push("b");
        DeepStack deepCopy = deep.clone();
        deepCopy.pop();
        deepCopy.push("X");
        System.out.println("deep:    original pops " + deep.pop() + " (expected b)");

        // Copy constructor: same result, final fields allowed, no casts
        CopyableStack source = new CopyableStack();
        source.push("a");
        source.push("b");
        CopyableStack copy = new CopyableStack(source);
        copy.pop();
        copy.push("X");
        System.out.println("copy constructor: original pops " + source.pop() + ", copy pops " + copy.pop());

        // Conversion constructors are a bonus: copy into a different type
        List<String> names = new ArrayList<>(List.of("kim", "lee", "kim", "choi"));
        System.out.println("TreeSet copy of a list: " + new TreeSet<>(names));

        // Arrays are the one place where clone is the right tool
        int[] primes = {2, 3, 5, 7};
        int[] primesCopy = primes.clone();
        primesCopy[0] = 99;
        System.out.println("array clone: " + Arrays.toString(primes) + " / " + Arrays.toString(primesCopy));

        // Without Cloneable, Object.clone refuses
        try {
            new NotCloneable().copy();
        } catch (CloneNotSupportedException e) {
            System.out.println("not Cloneable: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}

class ShallowStack implements Cloneable {
    private Object[] elements = new Object[8];
    private int size;

    void push(Object e) {
        elements[size++] = e;
    }

    Object pop() {
        Object result = elements[--size];
        elements[size] = null; // clears the slot in the SHARED array
        return result;
    }

    // Covariant return type: callers need no cast
    @Override
    public ShallowStack clone() {
        try {
            return (ShallowStack) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e); // cannot happen, we are Cloneable
        }
    }
}

class DeepStack implements Cloneable {
    private Object[] elements = new Object[8]; // not final: clone must reassign it
    private int size;

    void push(Object e) {
        elements[size++] = e;
    }

    Object pop() {
        Object result = elements[--size];
        elements[size] = null;
        return result;
    }

    @Override
    public DeepStack clone() {
        try {
            DeepStack result = (DeepStack) super.clone();
            result.elements = elements.clone();
            return result;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }
}

class CopyableStack {
    private final Object[] elements;
    private int size;

    CopyableStack() {
        elements = new Object[8];
    }

    // Copy constructor
    CopyableStack(CopyableStack other) {
        elements = Arrays.copyOf(other.elements, other.elements.length);
        size = other.size;
    }

    void push(Object e) {
        elements[size++] = e;
    }

    Object pop() {
        Object result = elements[--size];
        elements[size] = null;
        return result;
    }
}

class NotCloneable {
    Object copy() throws CloneNotSupportedException {
        return super.clone();
    }
}

/* Output:
shallow: original pops X (expected b)
deep:    original pops b (expected b)
copy constructor: original pops b, copy pops X
TreeSet copy of a list: [choi, kim, lee]
array clone: [2, 3, 5, 7] / [99, 3, 5, 7]
not Cloneable: CloneNotSupportedException: NotCloneable
*/
