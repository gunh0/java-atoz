// Comparison sorts cannot beat O(n log n), but sorts that look at the keys'
// digits can. Counting sort counts how often each key in a small range
// [min, max] occurs, turns the counts into starting positions (prefix sums)
// and places every element directly: O(n + k) for k possible keys. Walking
// the input from left to right while placing keeps it stable.
// LSD radix sort applies a stable counting sort once per digit, least
// significant first: 32-bit ints in base 256 take 4 passes of O(n + 256),
// regardless of n. Negative numbers sort correctly if the sign bit is
// flipped first (x ^ 0x80000000 maps int order onto unsigned order).
import java.util.Arrays;
import java.util.Random;

public class CountingRadixSort {

    // Stable counting sort of values in [min, max]
    static int[] countingSort(int[] a, int min, int max) {
        int[] count = new int[max - min + 2];
        for (int x : a) {
            count[x - min + 1]++;
        }
        for (int i = 1; i < count.length; i++) {
            count[i] += count[i - 1]; // count[v - min] = first output index for value v
        }
        int[] out = new int[a.length];
        for (int x : a) {
            out[count[x - min]++] = x;
        }
        return out;
    }

    // LSD radix sort, base 256, 4 passes; handles negatives via the sign-bit flip
    static void radixSort(int[] a) {
        int[] buf = new int[a.length];
        int[] src = a, dst = buf;
        for (int shift = 0; shift < 32; shift += 8) {
            int[] count = new int[257];
            for (int x : src) {
                count[digit(x, shift) + 1]++;
            }
            for (int i = 1; i < count.length; i++) {
                count[i] += count[i - 1];
            }
            for (int x : src) {
                dst[count[digit(x, shift)]++] = x;
            }
            int[] t = src;
            src = dst;
            dst = t;
        }
        // 4 passes (even): the result is back in `a`
    }

    private static int digit(int x, int shift) {
        return ((x ^ Integer.MIN_VALUE) >>> shift) & 0xFF;
    }

    public static void main(String[] args) {
        int[] grades = {3, 1, 4, 1, 5, 2, 0, 4, 3, 5, 1};
        System.out.println("counting sort of grades 0..5: " + Arrays.toString(countingSort(grades, 0, 5)));

        // Stability is what makes the LSD passes work: sort two-digit numbers by
        // ones digit, then (stably) by tens digit
        int[] nums = {53, 21, 49, 23, 51, 47};
        System.out.println("input:                 " + Arrays.toString(nums));
        Integer[] byOnes = Arrays.stream(nums).boxed().toArray(Integer[]::new);
        Arrays.sort(byOnes, (x, y) -> x % 10 - y % 10); // object sort is stable
        System.out.println("after ones-digit pass: " + Arrays.toString(byOnes));
        Arrays.sort(byOnes, (x, y) -> x / 10 - y / 10);
        System.out.println("after tens-digit pass: " + Arrays.toString(byOnes));

        int[] mixed = {170, -45, 75, -90, 802, 24, 2, 66, Integer.MIN_VALUE, Integer.MAX_VALUE, 0};
        radixSort(mixed);
        System.out.println("radix sort with negatives: " + Arrays.toString(mixed));

        // Self-check against Arrays.sort on random data, including the full int range
        Random rnd = new Random(10);
        boolean ok = true;
        for (int round = 0; round < 300; round++) {
            int[] small = rnd.ints(rnd.nextInt(500), -10, 10).toArray();
            int[] expectedSmall = small.clone();
            Arrays.sort(expectedSmall);
            ok &= Arrays.equals(countingSort(small, -10, 9), expectedSmall);

            int[] big = rnd.ints(rnd.nextInt(500)).toArray();
            int[] expectedBig = big.clone();
            Arrays.sort(expectedBig);
            radixSort(big);
            ok &= Arrays.equals(big, expectedBig);
        }
        System.out.println("300 random rounds match Arrays.sort: " + ok);
    }
}

/* Output:
counting sort of grades 0..5: [0, 1, 1, 1, 2, 3, 3, 4, 4, 5, 5]
input:                 [53, 21, 49, 23, 51, 47]
after ones-digit pass: [21, 51, 53, 23, 47, 49]
after tens-digit pass: [21, 23, 47, 49, 51, 53]
radix sort with negatives: [-2147483648, -90, -45, 0, 2, 24, 66, 75, 170, 802, 2147483647]
300 random rounds match Arrays.sort: true
*/
