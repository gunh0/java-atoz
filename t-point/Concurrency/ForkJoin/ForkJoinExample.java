// The fork/join framework runs divide-and-conquer work in parallel:
//  - a RecursiveTask<V> (or RecursiveAction, without result) splits its
//    input until it is small enough, then computes directly
//  - fork() schedules a subtask, join() waits for its result; the usual
//    shape is "fork the left half, compute the right half, join the left"
//  - a ForkJoinPool uses work stealing: idle workers take queued subtasks
//    from busy ones, so uneven splits still keep every core busy
// Parallel streams run on the common pool (ForkJoinPool.commonPool()).
// Here: count suspicious entries in a large array of request sizes.
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;

public class ForkJoinExample {
    static final int THRESHOLD = 10_000;

    // Count requests larger than the limit in data[from, to)
    static final class CountOversized extends RecursiveTask<Long> {
        private final int[] data;
        private final int from, to, limit;

        CountOversized(int[] data, int from, int to, int limit) {
            this.data = data;
            this.from = from;
            this.to = to;
            this.limit = limit;
        }

        @Override
        protected Long compute() {
            if (to - from <= THRESHOLD) {
                long count = 0;
                for (int i = from; i < to; i++) if (data[i] > limit) count++;
                return count;
            }
            int mid = (from + to) >>> 1;
            CountOversized left = new CountOversized(data, from, mid, limit);
            CountOversized right = new CountOversized(data, mid, to, limit);
            left.fork();                      // run the left half asynchronously
            long rightCount = right.compute(); // do the right half in this thread
            return left.join() + rightCount;   // wait for the left half
        }
    }

    // A RecursiveAction mutates in place and returns nothing: clamp oversized values
    static final class Clamp extends RecursiveAction {
        private final int[] data;
        private final int from, to, limit;

        Clamp(int[] data, int from, int to, int limit) {
            this.data = data;
            this.from = from;
            this.to = to;
            this.limit = limit;
        }

        @Override
        protected void compute() {
            if (to - from <= THRESHOLD) {
                for (int i = from; i < to; i++) data[i] = Math.min(data[i], limit);
                return;
            }
            int mid = (from + to) >>> 1;
            invokeAll(new Clamp(data, from, mid, limit), new Clamp(data, mid, to, limit));
        }
    }

    public static void main(String[] args) {
        // Deterministic pseudo-random request sizes from a simple LCG
        int[] sizes = new int[2_000_000];
        long x = 42;
        for (int i = 0; i < sizes.length; i++) {
            x = (x * 6364136223846793005L + 1442695040888963407L);
            sizes[i] = (int) ((x >>> 33) % 100_000);
        }
        int limit = 99_000;

        long sequential = Arrays.stream(sizes).filter(s -> s > limit).count();
        try (ForkJoinPool pool = new ForkJoinPool(4)) { // AutoCloseable since Java 19
            long parallel = pool.invoke(new CountOversized(sizes, 0, sizes.length, limit));
            System.out.println("oversized (sequential): " + sequential);
            System.out.println("oversized (fork/join):  " + parallel);
            System.out.println("same result: " + (sequential == parallel));

            pool.invoke(new Clamp(sizes, 0, sizes.length, limit));
            long after = pool.invoke(new CountOversized(sizes, 0, sizes.length, limit));
            System.out.println("oversized after clamping: " + after + ", max now: " + Arrays.stream(sizes).max().getAsInt());
        }
    }
}

/* Output:
oversized (sequential): 19796
oversized (fork/join):  19796
same result: true
oversized after clamping: 0, max now: 99000
*/
