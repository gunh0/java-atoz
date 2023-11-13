// Effective Java, Item 81: Prefer concurrency utilities to wait and notify.
//
// wait and notify are low-level primitives that are hard to use correctly:
// wait must be called in a loop that re-checks the condition (spurious
// wakeups, notifications meant for someone else), and choosing notify vs
// notifyAll is subtle. java.util.concurrent offers higher-level tools that
// make most uses unnecessary: concurrent collections (ConcurrentHashMap with
// atomic putIfAbsent/computeIfAbsent, BlockingQueue for producer-consumer)
// and synchronizers (CountDownLatch, Semaphore, Phaser, ...). Use them for
// new code; if you must maintain wait/notify code, use the standard
// "while (!condition) wait();" idiom.
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConcurrencyUtilitiesOverWaitNotify {
    // Old style: a hand-written bounded buffer with wait/notifyAll
    static final class WaitNotifyBuffer {
        private final Object[] items = new Object[2];
        private int head, tail, count;

        synchronized void put(Object x) throws InterruptedException {
            while (count == items.length) { // always wait in a loop
                wait();
            }
            items[tail] = x;
            tail = (tail + 1) % items.length;
            count++;
            notifyAll();
        }

        synchronized Object take() throws InterruptedException {
            while (count == 0) {
                wait();
            }
            Object x = items[head];
            head = (head + 1) % items.length;
            count--;
            notifyAll();
            return x;
        }
    }

    // Concurrent map: atomic "intern" without explicit locking
    private static final ConcurrentMap<String, String> pool = new ConcurrentHashMap<>();

    static String intern(String s) {
        String previous = pool.putIfAbsent(s, s);
        return previous == null ? s : previous;
    }

    public static void main(String[] args) throws Exception {
        // Producer/consumer, old and new
        WaitNotifyBuffer buffer = new WaitNotifyBuffer();
        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    buffer.put(i);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        producer.start();
        int sum = 0;
        for (int i = 0; i < 5; i++) {
            sum += (Integer) buffer.take();
        }
        producer.join();
        System.out.println("wait/notify buffer:   sum = " + sum);

        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(2);
        Thread producer2 = Thread.ofPlatform().start(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    queue.put(i);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        int sum2 = 0;
        for (int i = 0; i < 5; i++) {
            sum2 += queue.take();
        }
        producer2.join();
        System.out.println("ArrayBlockingQueue:   sum = " + sum2);

        // CountDownLatch: start N workers at the same moment, wait for all
        int workers = 8;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);
        List<String> interned = new ArrayList<>();
        try (ExecutorService exec = Executors.newFixedThreadPool(workers)) {
            for (int i = 0; i < workers; i++) {
                exec.execute(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        String s = intern(new String("token")); // all race to intern the same value
                        synchronized (interned) {
                            interned.add(s);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            ready.await();
            start.countDown();
            done.await();
        }
        boolean oneInstance = interned.stream().allMatch(s -> s == interned.get(0));
        System.out.println("latch: " + interned.size() + " workers finished, all got the same instance = "
                + oneInstance);
    }
}

/* Output:
wait/notify buffer:   sum = 15
ArrayBlockingQueue:   sum = 15
latch: 8 workers finished, all got the same instance = true
*/
