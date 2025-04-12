// Virtual threads from the inside: carriers, mounting, and parked continuations
//
// A virtual thread is a java.lang.Thread whose stack is a continuation
// object on the heap. To run, it is MOUNTED on a carrier: a platform thread
// of a dedicated ForkJoinPool (one carrier per CPU core by default, see
// jdk.virtualThreadScheduler.parallelism). When it blocks in a JDK API
// (sleep, a lock, a queue, socket I/O) the continuation yields: its frames
// are copied off the carrier stack and the carrier picks up another virtual
// thread. Unblocking resubmits it to the pool, possibly onto another carrier.
// What can be observed from Java, without timing assumptions:
//  - toString() of a mounted virtual thread names its carrier ("@ForkJoinPool-1-worker-1");
//  - a parked virtual thread has no carrier, and its stack trace (rebuilt
//    from the continuation) ends in VirtualThread.park;
//  - Thread.getAllStackTraces() lists the carriers but never virtual threads;
//  - thousands of blocked virtual threads share a handful of carriers.
// Only verdicts are printed: thread numbers and carrier assignment vary.
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

public class VirtualThreadInternals {
    public static void main(String[] args) throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        String[] whileRunning = new String[1];
        Thread vt = Thread.ofVirtual().name("worker").start(() -> {
            whileRunning[0] = Thread.currentThread().toString();
            started.countDown();
            try {
                release.await(); // parks: the continuation yields and the carrier is freed
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        started.await();
        while (vt.getState() != Thread.State.WAITING) Thread.onSpinWait();

        System.out.println("running: toString names a carrier:     " + whileRunning[0].contains("@ForkJoinPool-"));
        System.out.println("parked:  toString names a carrier:     " + vt.toString().contains("@") + ", state " + vt.getState());
        StackTraceElement[] parked = vt.getStackTrace();
        StackTraceElement top = parked[0], bottom = parked[parked.length - 1];
        System.out.println("parked stack, top frame:    " + top.getClassName() + "." + top.getMethodName());
        System.out.println("parked stack, bottom frame: " + bottom.getClassName() + "." + bottom.getMethodName());

        Set<Thread> platform = Thread.getAllStackTraces().keySet();
        System.out.println("getAllStackTraces() contains the virtual thread: " + platform.contains(vt));
        System.out.println("getAllStackTraces() contains a carrier:          "
                + platform.stream().anyMatch(t -> t.getName().startsWith("ForkJoinPool-") && !t.isVirtual()));
        System.out.println("carriers are daemon platform threads:            "
                + platform.stream().filter(t -> t.getName().startsWith("ForkJoinPool-")).allMatch(t -> t.isDaemon() && !t.isVirtual()));
        release.countDown();
        vt.join();

        // 10,000 virtual threads that each block once: how many carriers did they use?
        Set<String> carriers = ConcurrentHashMap.newKeySet();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 10_000; i++) {
                executor.submit(() -> {
                    carriers.add(carrierOf(Thread.currentThread()));
                    Thread.sleep(10);
                    carriers.add(carrierOf(Thread.currentThread()));
                    return null;
                });
            }
        }
        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("10,000 virtual threads used at most availableProcessors() carriers: " + (carriers.size() <= cores));
        boolean[] seesItself = new boolean[1];
        Thread.ofVirtual().start(() -> seesItself[0] = Thread.currentThread().isVirtual()).join();
        System.out.println("Thread.currentThread() inside is the virtual thread, not the carrier: " + seesItself[0]);
    }

    static String carrierOf(Thread t) {
        String s = t.toString(); // VirtualThread[#42]/runnable@ForkJoinPool-1-worker-3
        return s.substring(s.indexOf('@') + 1);
    }
}

/* Output:
running: toString names a carrier:     true
parked:  toString names a carrier:     false, state WAITING
parked stack, top frame:    java.lang.VirtualThread.park
parked stack, bottom frame: java.lang.VirtualThread.run
getAllStackTraces() contains the virtual thread: false
getAllStackTraces() contains a carrier:          true
carriers are daemon platform threads:            true
10,000 virtual threads used at most availableProcessors() carriers: true
Thread.currentThread() inside is the virtual thread, not the carrier: true
*/
