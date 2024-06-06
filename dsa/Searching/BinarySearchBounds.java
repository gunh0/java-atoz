// Binary search halves a sorted range each step: O(log n). The variants
// worth memorizing all use the same half-open loop over [lo, hi):
//   lowerBound(x) = first index with a[i] >= x
//   upperBound(x) = first index with a[i] >  x
// so the occurrences of x are exactly [lowerBound, upperBound), and the
// insertion point of a missing x is lowerBound. mid = (lo + hi) >>> 1 avoids
// the int overflow of (lo + hi) / 2 on huge arrays (a real JDK bug, fixed
// in 2006). The same loop works on any monotone predicate, which gives
// "binary search on the answer": find the smallest value that satisfies a
// condition, here the minimum ship capacity to deliver packages in D days.
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import java.util.function.IntPredicate;

public class BinarySearchBounds {

    // First index in [0, a.length] whose element is >= x
    static int lowerBound(int[] a, int x) {
        int lo = 0, hi = a.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (a[mid] < x) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    // First index in [0, a.length] whose element is > x
    static int upperBound(int[] a, int x) {
        int lo = 0, hi = a.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (a[mid] <= x) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    // Same contract as Arrays.binarySearch: index if found, else -(insertionPoint) - 1
    static int binarySearch(int[] a, int x) {
        int i = lowerBound(a, x);
        return i < a.length && a[i] == x ? i : -i - 1;
    }

    // Smallest v in [lo, hi] with ok(v) true, assuming ok is false...false true...true
    static int firstTrue(int lo, int hi, IntPredicate ok) {
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (ok.test(mid)) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return lo;
    }

    static int daysNeeded(int[] weights, int capacity) {
        int days = 1, load = 0;
        for (int w : weights) {
            if (load + w > capacity) {
                days++;
                load = 0;
            }
            load += w;
        }
        return days;
    }

    public static void main(String[] args) {
        int[] a = {1, 3, 3, 3, 5, 8, 8, 13};
        System.out.println("a = " + Arrays.toString(a));
        for (int x : new int[] {3, 8, 4, 0, 20}) {
            int lb = lowerBound(a, x), ub = upperBound(a, x);
            System.out.printf(Locale.ROOT, "x=%-2d lowerBound=%d upperBound=%d count=%d binarySearch=%d%n",
                    x, lb, ub, ub - lb, binarySearch(a, x));
        }

        // Binary search on the answer: capacity is monotone (bigger never needs more days)
        int[] packages = {3, 2, 2, 4, 1, 4, 7, 5, 6, 1};
        int days = 4;
        int lo = Arrays.stream(packages).max().getAsInt(); // must fit the heaviest package
        int hi = Arrays.stream(packages).sum();             // everything in one day
        int capacity = firstTrue(lo, hi, c -> daysNeeded(packages, c) <= days);
        System.out.println("min capacity to ship " + Arrays.toString(packages) + " in " + days
                + " days: " + capacity + " (needs " + daysNeeded(packages, capacity) + " days; "
                + (capacity - 1) + " needs " + daysNeeded(packages, capacity - 1) + ")");

        // Self-check against Arrays.binarySearch and a linear scan on random sorted arrays
        Random rnd = new Random(42);
        boolean ok = true;
        for (int round = 0; round < 2_000; round++) {
            int[] arr = rnd.ints(rnd.nextInt(30), 0, 20).sorted().toArray();
            int x = rnd.nextInt(22) - 1;
            long below = Arrays.stream(arr).filter(v -> v < x).count();
            long atMost = Arrays.stream(arr).filter(v -> v <= x).count();
            ok &= lowerBound(arr, x) == below && upperBound(arr, x) == atMost;
            int expected = Arrays.binarySearch(arr, x);
            int actual = binarySearch(arr, x);
            // With duplicates Arrays.binarySearch may return any matching index
            ok &= expected >= 0 ? actual >= 0 && arr[actual] == x : actual == expected;
        }
        System.out.println("2,000 random arrays agree with linear scan and Arrays.binarySearch: " + ok);
    }
}

/* Output:
a = [1, 3, 3, 3, 5, 8, 8, 13]
x=3  lowerBound=1 upperBound=4 count=3 binarySearch=1
x=8  lowerBound=5 upperBound=7 count=2 binarySearch=5
x=4  lowerBound=4 upperBound=4 count=0 binarySearch=-5
x=0  lowerBound=0 upperBound=0 count=0 binarySearch=-1
x=20 lowerBound=8 upperBound=8 count=0 binarySearch=-9
min capacity to ship [3, 2, 2, 4, 1, 4, 7, 5, 6, 1] in 4 days: 12 (needs 3 days; 11 needs 5)
2,000 random arrays agree with linear scan and Arrays.binarySearch: true
*/
