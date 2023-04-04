// Effective Java, Item 49: Check parameters for validity.
//
// A method should check its arguments as soon as it is entered and fail
// with a clear exception. If it does not, it may fail later with a confusing
// exception far from the cause, return a wrong result, or (worst) leave an
// object in a corrupt state that surfaces much later. Document the
// restrictions with @throws, and use the library helpers:
// Objects.requireNonNull, Objects.checkIndex / checkFromIndexSize (Java 9).
// Non-public methods, whose callers you control, can use assert instead;
// assertions cost nothing unless enabled with -ea. Skip a check only if it
// is expensive and the computation performs it implicitly anyway.
import java.math.BigInteger;
import java.util.Objects;

public class CheckParameters {
    public static void main(String[] args) {
        // Unchecked constructor: accepts null, explodes later somewhere else
        LazyAccount lazy = new LazyAccount(null);
        try {
            System.out.println(lazy.greeting());
        } catch (NullPointerException e) {
            System.out.println("late failure, far from the cause: NullPointerException");
        }

        // Checked constructor: fails immediately, naming the parameter
        try {
            new Account(null);
        } catch (NullPointerException e) {
            System.out.println("early failure: " + e.getMessage());
        }

        // Range checks with the Java 9 helpers
        int[] data = { 1, 2, 3 };
        try {
            element(data, 5);
        } catch (IndexOutOfBoundsException e) {
            System.out.println("checkIndex: " + e.getMessage());
        }
        try {
            sum(data, 1, 3);
        } catch (IndexOutOfBoundsException e) {
            System.out.println("checkFromIndexSize: " + e.getMessage());
        }
        System.out.println("sum(data, 1, 2) = " + sum(data, 1, 2));

        // A documented precondition
        try {
            mod(BigInteger.TEN, BigInteger.ZERO);
        } catch (ArithmeticException e) {
            System.out.println("mod: " + e.getMessage());
        }
        System.out.println("mod(10, 3) = " + mod(BigInteger.TEN, BigInteger.valueOf(3)));

        // Private helper with assert: disabled by default, so this does not throw
        System.out.println("assert in private method (no -ea): " + half(-4));
    }

    static int element(int[] a, int index) {
        return a[Objects.checkIndex(index, a.length)];
    }

    static int sum(int[] a, int from, int size) {
        Objects.checkFromIndexSize(from, size, a.length);
        int s = 0;
        for (int i = from; i < from + size; i++) s += a[i];
        return s;
    }

    /**
     * Returns a BigInteger whose value is (n mod m).
     *
     * @throws ArithmeticException if m is less than or equal to 0
     */
    static BigInteger mod(BigInteger n, BigInteger m) {
        if (m.signum() <= 0) {
            throw new ArithmeticException("modulus <= 0: " + m);
        }
        return n.mod(m);
    }

    private static int half(int x) {
        assert x >= 0 : x; // checked only with java -ea
        return x / 2;
    }
}

class LazyAccount {
    private final String owner;

    LazyAccount(String owner) {
        this.owner = owner; // no check
    }

    String greeting() {
        return "hello " + owner.toUpperCase();
    }
}

class Account {
    private final String owner;

    Account(String owner) {
        this.owner = Objects.requireNonNull(owner, "owner must not be null");
    }

    String greeting() {
        return "hello " + owner.toUpperCase();
    }
}

/* Output:
late failure, far from the cause: NullPointerException
early failure: owner must not be null
checkIndex: Index 5 out of bounds for length 3
checkFromIndexSize: Range [1, 1 + 3) out of bounds for length 3
sum(data, 1, 2) = 5
mod: modulus <= 0: 0
mod(10, 3) = 1
assert in private method (no -ea): -2
*/
