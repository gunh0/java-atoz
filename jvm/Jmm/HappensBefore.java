// Happens-before: the edges that make one thread's writes visible to another
//
// The JMM promises that if action A happens-before action B, B sees A's
// effects (and everything before A). Within one thread, program order gives
// happens-before. Between threads you need one of the edges JLS 17.4.5 and
// java.util.concurrent define: Thread.start() (everything before start is
// visible to the new thread), Thread.join() (everything the thread did is
// visible after join returns), unlocking a monitor or Lock before a later
// lock of the same one, a volatile write before a later read of it, and the
// library edges: submitting to an executor before the task runs, a task's
// completion before Future.get() returns, countDown() before await()
// returns, putting into a concurrent collection before taking out. Each
// demo below writes plain fields and relies on exactly one such edge, so its
// result is guaranteed, not lucky. A data race is any conflicting access
// pair NOT ordered by happens-before.
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class HappensBefore {
    static int plain;         // deliberately not volatile: the edges do the work
    static int[] array;

    public static void main(String[] args) throws Exception {
        // 1. Thread.start(): writes before start() are visible inside the thread
        plain = 1;
        int[] seenByThread = new int[1];
        Thread t = new Thread(() -> seenByThread[0] = plain);
        t.start();
        // 2. Thread.join(): the thread's writes are visible after join()
        t.join();
        System.out.println("start(): new thread saw " + seenByThread[0] + "; join(): main sees the thread's write " + seenByThread[0]);

        // 3. Monitor: unlock of 'lock' happens-before the next lock of 'lock'
        Object lock = new Object();
        Thread writer = new Thread(() -> {
            synchronized (lock) {
                array = new int[] {10, 20, 30};
            }
        });
        writer.start();
        int[] seen = null;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (seen == null && System.nanoTime() < deadline) {
            synchronized (lock) {     // no join here: only the monitor orders the accesses
                seen = array;
            }
        }
        System.out.println("synchronized: reader sees array " + seen[0] + "," + seen[1] + "," + seen[2]);
        writer.join();

        // 4. Executor: submit() before run(); task completion before get()
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            plain = 2;
            Future<Integer> f = pool.submit(() -> {
                int v = plain;    // sees 2: submit happens-before run
                plain = v * 10;
                return v;
            });
            System.out.println("executor: task saw " + f.get() + ", after get() main sees " + plain);

            // 5. CountDownLatch: countDown() before await() returns
            CountDownLatch done = new CountDownLatch(1);
            int[] result = new int[1];
            pool.execute(() -> {
                result[0] = 42;
                done.countDown();
            });
            if (!done.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
            System.out.println("latch: main sees " + result[0]);

            // 6. Concurrent queue: put() before the matching take()
            BlockingQueue<int[]> q = new LinkedBlockingQueue<>();
            pool.execute(() -> {
                int[] msg = new int[3];
                msg[0] = 7; msg[1] = 8; msg[2] = 9; // filled BEFORE put: safe to hand over
                q.add(msg);
            });
            int[] got = q.poll(5, TimeUnit.SECONDS);
            System.out.println("queue: consumer sees " + got[0] + "," + got[1] + "," + got[2]);
        }
    }
}

/* Output:
start(): new thread saw 1; join(): main sees the thread's write 1
synchronized: reader sees array 10,20,30
executor: task saw 2, after get() main sees 20
latch: main sees 42
queue: consumer sees 7,8,9
*/
