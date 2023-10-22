// A BlockingQueue does the wait/notify plumbing of a producer-consumer
// pipeline for you (compare Multithreading/WaitNotify). Each operation comes
// in three flavours for a full (insert) or empty (remove) queue:
//            throws        returns special value   blocks
//   insert   add(e)        offer(e) -> false        put(e)
//   remove   remove()      poll()   -> null         take()
// plus offer/poll with a timeout. A bounded queue (ArrayBlockingQueue) gives
// back-pressure: a fast producer is slowed to the consumers' pace.
// A "poison pill" element tells each consumer to stop.
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BlockingQueueExample {
    record LogLine(String level, String text) {}

    static final LogLine POISON = new LogLine("", ""); // compared by identity below
    static final int CONSUMERS = 2;

    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<LogLine> queue = new ArrayBlockingQueue<>(4);
        Map<String, Integer> countsByLevel = new ConcurrentHashMap<>();
        AtomicInteger alerts = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(CONSUMERS + 1);

        // Consumers: take() blocks while the queue is empty
        for (int c = 0; c < CONSUMERS; c++) {
            pool.execute(() -> {
                try {
                    while (true) {
                        LogLine line = queue.take();
                        if (line == POISON) return;
                        countsByLevel.merge(line.level(), 1, Integer::sum);
                        if (line.text().contains("denied")) alerts.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Producer: put() blocks while the queue is full (capacity 4)
        List<String> levels = List.of("INFO", "WARN", "INFO", "ERROR", "INFO");
        pool.execute(() -> {
            try {
                for (int i = 0; i < 100; i++) {
                    String level = levels.get(i % levels.size());
                    String text = level.equals("WARN") ? "access denied for user" + i : "request " + i;
                    queue.put(new LogLine(level, text));
                }
                for (int c = 0; c < CONSUMERS; c++) queue.put(POISON); // one pill per consumer
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        pool.shutdown();
        System.out.println("pipeline finished: " + pool.awaitTermination(5, TimeUnit.SECONDS));
        System.out.println("lines per level: " + new TreeMap<>(countsByLevel));
        System.out.println("denied alerts: " + alerts.get());
        System.out.println("queue empty: " + queue.isEmpty());

        // The non-blocking and timed variants on a full / empty queue
        BlockingQueue<String> small = new ArrayBlockingQueue<>(2);
        System.out.println("offer a: " + small.offer("a") + ", offer b: " + small.offer("b")
                + ", offer c: " + small.offer("c") + ", remaining capacity: " + small.remainingCapacity());
        try {
            small.add("c");
        } catch (IllegalStateException e) {
            System.out.println("add on full queue: IllegalStateException (" + e.getMessage() + ")");
        }
        System.out.println("offer c with 50 ms timeout: " + small.offer("c", 50, TimeUnit.MILLISECONDS));
        System.out.println("poll: " + small.poll() + ", poll: " + small.poll() + ", poll: " + small.poll());
        System.out.println("poll with 50 ms timeout on empty queue: " + small.poll(50, TimeUnit.MILLISECONDS));
    }
}

/* Output:
pipeline finished: true
lines per level: {ERROR=20, INFO=60, WARN=20}
denied alerts: 20
queue empty: true
offer a: true, offer b: true, offer c: false, remaining capacity: 0
add on full queue: IllegalStateException (Queue full)
offer c with 50 ms timeout: false
poll: a, poll: b, poll: null
poll with 50 ms timeout on empty queue: null
*/
