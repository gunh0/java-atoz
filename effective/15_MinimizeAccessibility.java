// Effective Java, Item 15: Minimize the accessibility of classes and members.
//
// Encapsulation lets a component change its internals without breaking
// anyone, so give every class and member the lowest access level that still
// works: private first, package-private if the package needs it, protected or
// public only for deliberate API. Package-private top-level classes are not
// part of the exported API at all. Instance fields of public classes should
// almost never be public, and a "public static final" field is only constant
// if it refers to an immutable object: a nonzero-length array is always
// mutable, so expose an unmodifiable List or return a fresh copy instead.
import java.util.Arrays;
import java.util.List;

public class MinimizeAccessibility {
    public static void main(String[] args) {
        // Anti-pattern: a public static final array can be changed by anyone
        LeakyRoles.VALUES[0] = "superuser";
        System.out.println("leaky roles after a client write: " + Arrays.toString(LeakyRoles.VALUES));

        // Fix 1: private array, public unmodifiable view
        try {
            SafeRoles.VALUES.set(0, "superuser");
        } catch (UnsupportedOperationException e) {
            System.out.println("unmodifiable list rejects the write: " + e.getClass().getSimpleName());
        }
        System.out.println("safe roles: " + SafeRoles.VALUES);

        // Fix 2: private array, accessor returns a copy
        String[] copy = SafeRoles.values();
        copy[0] = "superuser";
        System.out.println("copy changed, original intact: " + Arrays.toString(SafeRoles.values()));

        // Private members stay private; the public surface is small
        Counter counter = new Counter();
        counter.increment();
        counter.increment();
        System.out.println("counter: " + counter.value());
        System.out.println("Counter public methods: " + publicMethods(Counter.class));
    }

    static List<String> publicMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .map(m -> m.getName())
                .sorted()
                .toList();
    }
}

// Package-private top-level classes: implementation details of this "package"
class LeakyRoles {
    public static final String[] VALUES = {"reader", "writer", "admin"};
}

class SafeRoles {
    private static final String[] PRIVATE_VALUES = {"reader", "writer", "admin"};

    public static final List<String> VALUES = List.of(PRIVATE_VALUES);

    public static String[] values() {
        return PRIVATE_VALUES.clone();
    }
}

class Counter {
    private int count; // private: the class can switch to long, AtomicInteger, ...

    public void increment() {
        count = checkedNext(count);
    }

    public int value() {
        return count;
    }

    // Helper is private: free to change or remove later
    private static int checkedNext(int n) {
        return Math.addExact(n, 1);
    }
}

/* Output:
leaky roles after a client write: [superuser, writer, admin]
unmodifiable list rejects the write: UnsupportedOperationException
safe roles: [reader, writer, admin]
copy changed, original intact: [reader, writer, admin]
counter: 2
Counter public methods: [increment, value]
*/
