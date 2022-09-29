// Effective Java, Item 27: Eliminate unchecked warnings.
//
// Every unchecked warning marks a place where the compiler cannot prove type
// safety, and a potential ClassCastException far away from the real mistake.
// Eliminate every warning you can (often a diamond or a type parameter is
// enough). If one remains and you can PROVE the code is safe, suppress it
// with @SuppressWarnings("unchecked") on the smallest possible scope, such as
// a single local variable declaration, never a whole class, and add a comment
// saying why it is safe. Compile with -Xlint:unchecked to see them all.
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UncheckedWarnings {
    public static void main(String[] args) {
        // Unchecked conversion: compiles with a warning, fails much later
        List<String> names = legacyNames();
        try {
            String first = names.get(0); // the cast inserted here throws
            System.out.println("first: " + first);
        } catch (ClassCastException e) {
            System.out.println("raw list: ClassCastException when READING, far from the bad add");
        }

        // Warning eliminated: the compiler now checks every add
        List<String> checked = new ArrayList<>();
        checked.add("kim");
        // checked.add(42);  // does not compile
        System.out.println("parameterized list: " + checked);

        // A warning that cannot be eliminated, suppressed narrowly
        SimpleList<String> list = new SimpleList<>();
        list.add("a");
        list.add("b");
        String[] out = list.toArray(new String[0]);
        System.out.println("toArray: " + Arrays.toString(out) + " as " + out.getClass().getSimpleName());
    }

    @SuppressWarnings({"rawtypes", "unchecked"}) // the anti-pattern, kept for the demo
    static List<String> legacyNames() {
        List raw = new ArrayList();
        raw.add(42);
        return raw;
    }
}

class SimpleList<E> {
    private Object[] elements = new Object[10];
    private int size;

    void add(E e) {
        elements[size++] = e;
    }

    <T> T[] toArray(T[] a) {
        if (a.length < size) {
            // This cast is correct because the array we create has the same
            // runtime type as the one passed in, which is T[].
            @SuppressWarnings("unchecked")
            T[] result = (T[]) Arrays.copyOf(elements, size, a.getClass());
            return result;
        }
        System.arraycopy(elements, 0, a, 0, size);
        if (a.length > size) {
            a[size] = null;
        }
        return a;
    }
}

/* Output:
raw list: ClassCastException when READING, far from the bad add
parameterized list: [kim]
toArray: [a, b] as String[]
*/
