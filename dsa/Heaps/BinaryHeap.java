// A binary heap is a complete binary tree stored in an array: the children
// of index i are 2i+1 and 2i+2, its parent is (i-1)/2, so no pointers are
// needed. The min-heap rule (every parent <= its children) puts the minimum
// at index 0. offer() appends at the end and sifts the element up while it
// is smaller than its parent; poll() moves the last element to the root and
// sifts it down, swapping with the smaller child. Both are O(log n), peek
// is O(1). Building a heap from n elements by sifting down every internal
// node from the bottom up (heapify) is O(n), cheaper than n offers.
// A heap is the standard priority queue (java.util.PriorityQueue is one);
// note that iterating the array does not give sorted order, only polling does.
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.Random;

public class BinaryHeap {

    static final class MinHeap<E> {
        private Object[] heap;
        private int size;
        private final Comparator<? super E> cmp;
        long compares;

        MinHeap(Comparator<? super E> cmp) {
            this.cmp = cmp;
            this.heap = new Object[8];
        }

        // Heapify: O(n) bottom-up construction
        MinHeap(List<? extends E> items, Comparator<? super E> cmp) {
            this.cmp = cmp;
            this.heap = items.toArray(new Object[Math.max(8, items.size())]);
            this.size = items.size();
            for (int i = size / 2 - 1; i >= 0; i--) { // last internal node down to the root
                siftDown(i);
            }
        }

        int size() { return size; }
        boolean isEmpty() { return size == 0; }

        void offer(E e) {
            if (size == heap.length) {
                heap = Arrays.copyOf(heap, size * 2);
            }
            heap[size] = e;
            siftUp(size++);
        }

        @SuppressWarnings("unchecked")
        E peek() {
            if (size == 0) {
                throw new NoSuchElementException("empty heap");
            }
            return (E) heap[0];
        }

        E poll() {
            E min = peek();
            heap[0] = heap[--size];
            heap[size] = null;
            if (size > 0) {
                siftDown(0);
            }
            return min;
        }

        private void siftUp(int i) {
            while (i > 0) {
                int parent = (i - 1) / 2;
                if (less(i, parent)) {
                    swap(i, parent);
                    i = parent;
                } else {
                    return;
                }
            }
        }

        private void siftDown(int i) {
            while (true) {
                int left = 2 * i + 1, right = left + 1, smallest = i;
                if (left < size && less(left, smallest)) {
                    smallest = left;
                }
                if (right < size && less(right, smallest)) {
                    smallest = right;
                }
                if (smallest == i) {
                    return;
                }
                swap(i, smallest);
                i = smallest;
            }
        }

        @SuppressWarnings("unchecked")
        private boolean less(int i, int j) {
            compares++;
            return cmp.compare((E) heap[i], (E) heap[j]) < 0;
        }

        private void swap(int i, int j) {
            Object t = heap[i];
            heap[i] = heap[j];
            heap[j] = t;
        }

        String array() {
            return Arrays.toString(Arrays.copyOf(heap, size));
        }
    }

    record Job(String name, int priority) { }

    public static void main(String[] args) {
        MinHeap<Integer> h = new MinHeap<>(Comparator.naturalOrder());
        for (int x : new int[] {42, 7, 19, 3, 25, 1}) {
            h.offer(x);
            System.out.printf(Locale.ROOT, "offer %-3d -> %s%n", x, h.array());
        }
        StringBuilder order = new StringBuilder();
        while (!h.isEmpty()) {
            order.append(h.poll()).append(' ');
        }
        System.out.println("poll order: " + order.toString().trim());

        // Any comparator: a max-heap of jobs by priority, ties by name
        MinHeap<Job> jobs = new MinHeap<>(Comparator.comparingInt(Job::priority).reversed()
                .thenComparing(Job::name));
        jobs.offer(new Job("rotate-keys", 5));
        jobs.offer(new Job("scan-ports", 2));
        jobs.offer(new Job("patch-kernel", 9));
        jobs.offer(new Job("audit-logs", 5));
        while (!jobs.isEmpty()) {
            Job j = jobs.poll();
            System.out.println("run " + j.name() + " (priority " + j.priority() + ")");
        }

        // Heapify vs n offers on descending input, the worst case for offer():
        // every new element is the new minimum and sifts all the way up
        List<Integer> data = java.util.stream.IntStream.range(0, 100_000).map(i -> 100_000 - i).boxed().toList();
        MinHeap<Integer> byOffer = new MinHeap<>(Comparator.naturalOrder());
        data.forEach(byOffer::offer);
        MinHeap<Integer> byHeapify = new MinHeap<>(data, Comparator.naturalOrder());
        System.out.printf(Locale.ROOT, "build 100,000 descending: offer one by one %,d compares, heapify %,d compares%n",
                byOffer.compares, byHeapify.compares);

        // Self-check against java.util.PriorityQueue
        Random rnd = new Random(12);
        MinHeap<Integer> mine = new MinHeap<>(Comparator.naturalOrder());
        PriorityQueue<Integer> ref = new PriorityQueue<>();
        boolean same = true;
        for (int op = 0; op < 50_000; op++) {
            if (ref.isEmpty() || rnd.nextInt(5) < 3) {
                int v = rnd.nextInt(10_000);
                mine.offer(v);
                ref.offer(v);
            } else {
                same &= mine.poll().equals(ref.poll());
            }
        }
        List<Integer> drained = new ArrayList<>();
        while (!byHeapify.isEmpty()) {
            drained.add(byHeapify.poll());
        }
        same &= drained.equals(data.stream().sorted().toList());
        System.out.println("50,000 random ops match PriorityQueue, heapify drains sorted: " + same);
    }
}

/* Output:
offer 42  -> [42]
offer 7   -> [7, 42]
offer 19  -> [7, 42, 19]
offer 3   -> [3, 7, 19, 42]
offer 25  -> [3, 7, 19, 42, 25]
offer 1   -> [1, 7, 3, 42, 25, 19]
poll order: 1 3 7 19 25 42
run patch-kernel (priority 9)
run audit-logs (priority 5)
run rotate-keys (priority 5)
run scan-ports (priority 2)
build 100,000 descending: offer one by one 1,468,946 compares, heapify 199,978 compares
50,000 random ops match PriorityQueue, heapify drains sorted: true
*/
