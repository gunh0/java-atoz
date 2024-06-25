// Quicksort picks a pivot, partitions the array around it and recurses on
// the parts: O(n log n) on average, in place, not stable. Two classic
// failure modes turn it into O(n^2): a predictable pivot (first element on
// already-sorted input) and many equal keys (a 2-way partition keeps
// recursing on runs of duplicates). A random pivot fixes the first;
// Dijkstra's 3-way partition fixes the second by splitting into
//   [ < pivot | == pivot | > pivot ]
// and recursing only on the outer parts, so an array of k distinct values
// costs O(n log k). The demo counts comparisons to show both effects.
// The random generator is seeded so the output is reproducible; a real
// sort exposed to adversarial input should use an unpredictable seed.
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

public class QuickSort {

    static long comparisons;

    static void sort3Way(int[] a, Random rnd) {
        sort3Way(a, 0, a.length - 1, rnd);
    }

    private static void sort3Way(int[] a, int lo, int hi, Random rnd) {
        if (lo >= hi) {
            return;
        }
        swap(a, lo, lo + rnd.nextInt(hi - lo + 1)); // random pivot to the front
        int pivot = a[lo];
        // Invariant: a[lo..lt-1] < pivot, a[lt..i-1] == pivot, a[gt+1..hi] > pivot
        int lt = lo, i = lo + 1, gt = hi;
        while (i <= gt) {
            comparisons++;
            if (a[i] < pivot) {
                swap(a, lt++, i++);
            } else if (a[i] > pivot) {
                swap(a, i, gt--); // the swapped-in element is unexamined: don't advance i
            } else {
                i++;
            }
        }
        sort3Way(a, lo, lt - 1, rnd);
        sort3Way(a, gt + 1, hi, rnd);
    }

    // Textbook 2-way Lomuto partition with the last element as pivot, for comparison
    static void sortNaive(int[] a, int lo, int hi) {
        while (lo < hi) {
            int pivot = a[hi], p = lo;
            for (int i = lo; i < hi; i++) {
                comparisons++;
                if (a[i] < pivot) {
                    swap(a, i, p++);
                }
            }
            swap(a, p, hi);
            // Recurse on the smaller side, loop on the larger: stack depth stays O(log n)
            if (p - lo < hi - p) {
                sortNaive(a, lo, p - 1);
                lo = p + 1;
            } else {
                sortNaive(a, p + 1, hi);
                hi = p - 1;
            }
        }
    }

    private static void swap(int[] a, int i, int j) {
        int t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    public static void main(String[] args) {
        int[] demo = {4, 9, 4, 1, 4, 7, 2, 4, 9, 1};
        sort3Way(demo, new Random(1));
        System.out.println("3-way sorted: " + Arrays.toString(demo));

        int n = 20_000;
        int[][] inputs = {
                new Random(2).ints(n, 0, 1_000_000).toArray(), // random
                java.util.stream.IntStream.range(0, n).toArray(), // already sorted
                new Random(3).ints(n, 0, 3).toArray(),         // only 3 distinct keys
        };
        String[] names = {"random", "sorted", "3 distinct keys"};
        System.out.printf(Locale.ROOT, "%-16s %14s %14s%n", "input (n=20,000)", "naive 2-way", "random 3-way");
        for (int k = 0; k < inputs.length; k++) {
            int[] x = inputs[k].clone();
            comparisons = 0;
            sortNaive(x, 0, x.length - 1);
            long naive = comparisons;
            int[] y = inputs[k].clone();
            comparisons = 0;
            sort3Way(y, new Random(4));
            System.out.printf(Locale.ROOT, "%-16s %,14d %,14d%n", names[k], naive, comparisons);
        }

        // Self-check against Arrays.sort
        Random rnd = new Random(5);
        boolean ok = true;
        for (int round = 0; round < 1_000; round++) {
            int[] arr = rnd.ints(rnd.nextInt(300), -20, rnd.nextInt(100) + 1).toArray();
            int[] expected = arr.clone();
            Arrays.sort(expected);
            int[] b = arr.clone();
            sort3Way(arr, rnd);
            sortNaive(b, 0, b.length - 1);
            ok &= Arrays.equals(arr, expected) && Arrays.equals(b, expected);
        }
        System.out.println("1,000 random arrays match Arrays.sort: " + ok);
    }
}

/* Output:
3-way sorted: [1, 1, 2, 4, 4, 4, 4, 7, 9, 9]
input (n=20,000)    naive 2-way   random 3-way
random                  335,183        331,619
sorted              199,990,000        325,572
3 distinct keys      66,689,820         39,895
1,000 random arrays match Arrays.sort: true
*/
