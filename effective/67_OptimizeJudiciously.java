// Effective Java, Item 67: Optimize judiciously.
//
// Strive to write good programs rather than fast ones; speed tends to
// follow from a sound design. The decisions that are hardest to change later
// are the ones that fix performance forever: API types, wire formats and
// whether a value is mutable. An accessor returning a mutable object, for
// example, forces a defensive copy on every call for the life of the API.
// Micro-optimizations, on the other hand, are cheap to add later and easy to
// get wrong. Measure before and after each attempt; a "clever" trick that is
// not measurably faster is just a bug waiting to happen.
import java.util.ArrayList;
import java.util.List;

public class OptimizeJudiciously {
    // Design decision 1: a mutable value type returned from an accessor
    static final class MutableSize {
        int width, height;
        static int copies;

        MutableSize(int width, int height) {
            this.width = width;
            this.height = height;
            copies++;
        }
    }

    static final class Widget {
        private final MutableSize size = new MutableSize(640, 480);

        MutableSize getSize() {
            return new MutableSize(size.width, size.height); // must copy, forever
        }
    }

    // Design decision 2: an immutable value can be shared freely
    record Size(int width, int height) {}

    static final class BetterWidget {
        private final Size size = new Size(640, 480);

        Size size() {
            return size; // no copy needed
        }
    }

    // A hand-rolled "fast" check versus the library
    static boolean isPowerOfTwoClever(int n) {
        return (n & (n - 1)) == 0;
    }

    static boolean isPowerOfTwo(int n) {
        return n > 0 && Integer.bitCount(n) == 1;
    }

    public static void main(String[] args) {
        Widget w = new Widget();
        MutableSize.copies = 0;
        long area = 0;
        for (int i = 0; i < 10_000; i++) {
            MutableSize s = w.getSize();
            area += (long) s.width * s.height;
        }
        System.out.println("mutable API: 10000 calls -> " + MutableSize.copies + " defensive copies");

        BetterWidget b = new BetterWidget();
        long area2 = 0;
        List<Size> distinct = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            Size s = b.size();
            area2 += (long) s.width() * s.height();
            if (distinct.stream().noneMatch(x -> x == s)) {
                distinct.add(s);
            }
        }
        System.out.println("immutable API: 10000 calls -> " + distinct.size() + " shared instance, same total: "
                + (area == area2));

        // The micro-optimization is wrong at the edges; the clear version is not
        for (int n : new int[] {0, 1, 6, 64, Integer.MIN_VALUE}) {
            System.out.printf("n=%-11d clever=%-5b correct=%b%n", n, isPowerOfTwoClever(n), isPowerOfTwo(n));
        }
    }
}

/* Output:
mutable API: 10000 calls -> 10000 defensive copies
immutable API: 10000 calls -> 1 shared instance, same total: true
n=0           clever=true  correct=false
n=1           clever=true  correct=true
n=6           clever=false correct=false
n=64          clever=true  correct=true
n=-2147483648 clever=true  correct=false
*/
