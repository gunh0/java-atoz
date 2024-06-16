// Merge sort splits the array in half, sorts each half recursively and
// merges the two sorted halves with two pointers. It is O(n log n) in every
// case and stable (equal keys keep their order, because the merge takes
// from the left half on ties), at the cost of an O(n) buffer. That is why
// Java sorts objects with a merge sort variant (TimSort) and primitives,
// where stability is invisible, with a quicksort.
// Bottom-up merge sort does the same merges without recursion: runs of
// width 1, 2, 4, ... The merge step can also count inversions (pairs
// i < j with a[i] > a[j]): whenever an element from the right half is taken
// first, it is smaller than every element left in the left half.
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class MergeSort {

    // Top-down; returns the number of inversions it removed
    static long sort(int[] a) {
        return sort(a, new int[a.length], 0, a.length);
    }

    private static long sort(int[] a, int[] buf, int lo, int hi) {
        if (hi - lo < 2) {
            return 0;
        }
        int mid = (lo + hi) >>> 1;
        long inv = sort(a, buf, lo, mid) + sort(a, buf, mid, hi);
        if (a[mid - 1] <= a[mid]) {
            return inv; // halves already in order: skip the merge
        }
        return inv + merge(a, buf, lo, mid, hi);
    }

    // Merge a[lo, mid) and a[mid, hi), both sorted
    private static long merge(int[] a, int[] buf, int lo, int mid, int hi) {
        System.arraycopy(a, lo, buf, lo, hi - lo);
        long inv = 0;
        int i = lo, j = mid;
        for (int k = lo; k < hi; k++) {
            if (i == mid) {
                a[k] = buf[j++];
            } else if (j == hi) {
                a[k] = buf[i++];
            } else if (buf[j] < buf[i]) {
                inv += mid - i; // buf[j] jumps over everything left in the left half
                a[k] = buf[j++];
            } else {
                a[k] = buf[i++]; // <= takes left first: stable
            }
        }
        return inv;
    }

    // Bottom-up for objects with a comparator, to show stability
    static <T> void sortBottomUp(T[] a, Comparator<? super T> cmp) {
        @SuppressWarnings("unchecked")
        T[] buf = (T[]) new Object[a.length];
        for (int width = 1; width < a.length; width *= 2) {
            for (int lo = 0; lo < a.length - width; lo += 2 * width) {
                int mid = lo + width, hi = Math.min(lo + 2 * width, a.length);
                System.arraycopy(a, lo, buf, lo, hi - lo);
                int i = lo, j = mid;
                for (int k = lo; k < hi; k++) {
                    if (i < mid && (j == hi || cmp.compare(buf[i], buf[j]) <= 0)) {
                        a[k] = buf[i++];
                    } else {
                        a[k] = buf[j++];
                    }
                }
            }
        }
    }

    record Employee(String name, String dept) { }

    public static void main(String[] args) {
        int[] a = {5, 2, 9, 1, 5, 6, 3};
        long inversions = sort(a);
        System.out.println("sorted: " + Arrays.toString(a) + ", inversions removed: " + inversions);

        // Stable: within a department, the original (alphabetical) order survives
        Employee[] staff = List.of(
                new Employee("Alice", "sec"), new Employee("Bob", "ops"), new Employee("Carol", "dev"),
                new Employee("Dave", "sec"), new Employee("Erin", "dev"), new Employee("Frank", "ops"))
                .toArray(new Employee[0]);
        sortBottomUp(staff, Comparator.comparing(Employee::dept));
        StringBuilder sb = new StringBuilder();
        for (Employee e : staff) {
            sb.append(e.dept()).append(':').append(e.name()).append(' ');
        }
        System.out.println("by dept, stable: " + sb.toString().trim());

        // Self-check: Arrays.sort and a brute-force O(n^2) inversion count
        Random rnd = new Random(8);
        boolean ok = true;
        for (int round = 0; round < 500; round++) {
            int[] arr = rnd.ints(rnd.nextInt(200), -50, 50).toArray();
            long brute = 0;
            for (int i = 0; i < arr.length; i++) {
                for (int j = i + 1; j < arr.length; j++) {
                    if (arr[i] > arr[j]) {
                        brute++;
                    }
                }
            }
            int[] expected = arr.clone();
            Arrays.sort(expected);
            ok &= sort(arr) == brute && Arrays.equals(arr, expected);

            Integer[] boxed = rnd.ints(rnd.nextInt(200), 0, 10).boxed().toArray(Integer[]::new);
            Integer[] boxedExpected = boxed.clone();
            Arrays.sort(boxedExpected);
            sortBottomUp(boxed, Comparator.naturalOrder());
            ok &= Arrays.equals(boxed, boxedExpected);
        }
        System.out.println("500 random arrays match Arrays.sort and brute-force inversions: " + ok);
    }
}

/* Output:
sorted: [1, 2, 3, 5, 5, 6, 9], inversions removed: 10
by dept, stable: dev:Carol dev:Erin ops:Bob ops:Frank sec:Alice sec:Dave
500 random arrays match Arrays.sort and brute-force inversions: true
*/
