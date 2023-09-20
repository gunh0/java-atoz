// JEP 444 — Virtual Threads (Java 21)
//
// A platform thread is a thin wrapper around an OS thread: it reserves
// a large stack and costs a kernel context switch, so a server can afford
// a few thousand of them. Thread-per-request code is simple to write and
// debug, but it runs out of threads long before it runs out of CPU when
// requests mostly wait on I/O; the workaround was asynchronous, callback
// style code that is hard to read and to profile. A virtual thread is a
// java.lang.Thread scheduled by the JDK onto a small pool of carrier
// threads; when it blocks (sleep, socket read, lock wait) it is unmounted
// and its carrier runs another one. They are cheap enough to create one
// per task, by the hundred thousand, and keep the plain blocking style.
// New entry points: Thread.ofVirtual(), Thread.startVirtualThread(),
// Thread.Builder and Thread.isVirtual().
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class VirtualThreads {
    public static void main(String[] args) throws InterruptedException {
        // The familiar API: a virtual thread IS a Thread
        Thread vt = Thread.startVirtualThread(() ->
                System.out.println("hello from a thread, virtual = " + Thread.currentThread().isVirtual()));
        vt.join();
        System.out.println("daemon = " + vt.isDaemon() + " (always), main is virtual = "
                + Thread.currentThread().isVirtual());

        // Builders for both kinds, with names and counters
        Thread.Builder platform = Thread.ofPlatform().name("worker-", 0);
        Thread.Builder virtual = Thread.ofVirtual().name("request-", 0);
        Thread p = platform.unstarted(() -> { });
        Thread v = virtual.unstarted(() -> { });
        System.out.println(p.getName() + " virtual=" + p.isVirtual() + ", " + v.getName() + " virtual=" + v.isVirtual());

        // A ThreadFactory for code that expects one (executors, libraries)
        ThreadFactory factory = Thread.ofVirtual().name("job-", 1).factory();
        Thread job = factory.newThread(() -> { });
        System.out.println("factory thread: " + job.getName() + ", virtual=" + job.isVirtual());

        // The point: 10,000 threads that each block for 200 ms finish together,
        // because a blocked virtual thread releases its carrier
        int n = 10_000;
        AtomicInteger done = new AtomicInteger();
        List<Thread> threads = new ArrayList<>(n);
        long start = System.nanoTime();
        for (int i = 0; i < n; i++) {
            threads.add(Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(Duration.ofMillis(200)); // a stand-in for blocking I/O
                    done.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        for (Thread t : threads) {
            t.join();
        }
        long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        // Sequentially this would take 2,000 seconds; with 10,000 platform threads,
        // 10,000 OS threads. Print a deterministic summary, not the timing.
        System.out.println(done.get() + " blocking tasks completed, under 10 s: " + (millis < 10_000));

        // Not supported for virtual threads: changing priority or daemon status
        Thread another = Thread.ofVirtual().unstarted(() -> { });
        another.setPriority(Thread.MAX_PRIORITY); // silently ignored
        System.out.println("priority stays " + another.getPriority());
        try {
            another.setDaemon(false);
        } catch (IllegalArgumentException e) {
            System.out.println("setDaemon(false): IllegalArgumentException");
        }
    }
}

/* Output:
hello from a thread, virtual = true
daemon = true (always), main is virtual = false
worker-0 virtual=false, request-0 virtual=true
factory thread: job-1, virtual=true
10000 blocking tasks completed, under 10 s: true
priority stays 5
setDaemon(false): IllegalArgumentException
*/
