// Heap sort: turn the array into a max-heap in place (bottom-up heapify, O(n)),
// then repeatedly swap the maximum at index 0 to the end of the unsorted part and
// sift the new root down. O(n log n) in the worst case, O(1) extra memory, but not
// stable and cache-unfriendly, which is why library sorts prefer merge/quick sort.
// Children of node i in an array heap live at 2i+1 and 2i+2; the parent at (i-1)/2.
import java.util.Arrays;
import java.util.Random;

public class HeapSort {
    static void sort(int[] a) {
        int n = a.length;
        // Heapify: sift down every internal node, last one first
        for (int i = n / 2 - 1; i >= 0; i--) {
            siftDown(a, i, n);
        }
        // Move the max behind the heap, shrink the heap, restore the heap property
        for (int end = n - 1; end > 0; end--) {
            swap(a, 0, end);
            siftDown(a, 0, end);
        }
    }

    // Sift a[i] down within a[0..size) until both children are smaller
    static void siftDown(int[] a, int i, int size) {
        while (true) {
            int left = 2 * i + 1;
            if (left >= size) {
                return;
            }
            int largest = left;
            int right = left + 1;
            if (right < size && a[right] > a[left]) {
                largest = right;
            }
            if (a[i] >= a[largest]) {
                return;
            }
            swap(a, i, largest);
            i = largest;
        }
    }

    static void swap(int[] a, int i, int j) {
        int t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    static boolean isMaxHeap(int[] a) {
        for (int i = 1; i < a.length; i++) {
            if (a[(i - 1) / 2] < a[i]) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        int[] demo = {5, 13, 2, 25, 7, 17, 20, 8, 4};
        int[] heap = demo.clone();
        for (int i = heap.length / 2 - 1; i >= 0; i--) {
            siftDown(heap, i, heap.length);
        }
        System.out.println("input:    " + Arrays.toString(demo));
        System.out.println("max-heap: " + Arrays.toString(heap) + " valid=" + isMaxHeap(heap));
        sort(demo);
        System.out.println("sorted:   " + Arrays.toString(demo));

        // Edge cases
        int[][] edges = {{}, {1}, {2, 1}, {3, 3, 3}, {Integer.MAX_VALUE, Integer.MIN_VALUE, 0}};
        for (int[] e : edges) {
            int[] copy = e.clone();
            sort(copy);
            System.out.println("edge " + Arrays.toString(e) + " -> " + Arrays.toString(copy));
        }

        // Self-check against Arrays.sort on seeded random inputs
        Random rnd = new Random(14);
        int trials = 500;
        boolean allMatch = true;
        for (int t = 0; t < trials; t++) {
            int[] a = rnd.ints(rnd.nextInt(200), -1000, 1000).toArray();
            int[] expected = a.clone();
            Arrays.sort(expected);
            sort(a);
            allMatch &= Arrays.equals(a, expected);
        }
        System.out.println(trials + " random arrays match Arrays.sort: " + allMatch);
    }
}

/* Output:
input:    [5, 13, 2, 25, 7, 17, 20, 8, 4]
max-heap: [25, 13, 20, 8, 7, 17, 2, 5, 4] valid=true
sorted:   [2, 4, 5, 7, 8, 13, 17, 20, 25]
edge [] -> []
edge [1] -> [1]
edge [2, 1] -> [1, 2]
edge [3, 3, 3] -> [3, 3, 3]
edge [2147483647, -2147483648, 0] -> [-2147483648, 0, 2147483647]
500 random arrays match Arrays.sort: true
*/
