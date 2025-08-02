// JEP 491 — Synchronize Virtual Threads without Pinning (Java 24)
//
// A virtual thread that blocks normally unmounts and frees its carrier
// (java21/VirtualThreads.java). Up to Java 23 there was a big exception:
// blocking while holding a monitor (inside a synchronized block or method,
// or in Object.wait) PINNED the virtual thread to its carrier, because the
// JVM tracked monitor ownership by carrier thread. A few such blocks under
// load could tie up every carrier and stall the application; the advice was
// to replace synchronized with ReentrantLock, and -Djdk.tracePinnedThreads
// printed a stack trace whenever pinning happened. Java 24 makes monitors
// track the virtual thread itself, so it can unmount inside synchronized like
// anywhere else; jdk.tracePinnedThreads is gone, and the JFR event
// jdk.VirtualThreadPinned reports the cases that remain (native frames,
// class initializers). Below, the scheduler gets a single carrier: a second
// virtual thread can only run if the first one releases it. (On Java 23 the
// same program prints false, one pinned event, and a pinning stack trace.)
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;

public class SynchronizeWithoutPinning {
    static final Object LOCK = new Object();
    static final AtomicBoolean SLOW_INIT_RUNNING = new AtomicBoolean();

    static class SlowInit {
        static {
            SLOW_INIT_RUNNING.set(true);
            sleep(200); // blocking inside <clinit> still pins
            SLOW_INIT_RUNNING.set(false);
        }
        static void touch() {}
    }

    public static void main(String[] args) throws Exception {
        // Must be set before the first virtual thread exists
        System.setProperty("jdk.virtualThreadScheduler.parallelism", "1");
        System.setProperty("jdk.virtualThreadScheduler.maxPoolSize", "1");
        System.setProperty("jdk.tracePinnedThreads", "full"); // honoured up to Java 23

        PrintStream console = System.out;
        ByteArrayOutputStream traced = new ByteArrayOutputStream();
        System.setOut(new PrintStream(traced, true));
        boolean ranDuringSynchronized, ranDuringClinit;
        long pinnedSynchronized, pinnedClinit;
        try {
            // 1. Sleep while holding a monitor
            AtomicBoolean sleeping = new AtomicBoolean();
            AtomicBoolean otherRan = new AtomicBoolean();
            pinnedSynchronized = countPinnedEvents(() -> {
                CountDownLatch inside = new CountDownLatch(1);
                Thread holder = Thread.ofVirtual().start(() -> {
                    synchronized (LOCK) {
                        sleeping.set(true);
                        inside.countDown();
                        sleep(300);
                        sleeping.set(false);
                    }
                });
                await(inside);
                Thread other = Thread.ofVirtual().start(() -> otherRan.set(sleeping.get()));
                join(holder);
                join(other);
            });
            ranDuringSynchronized = otherRan.get();

            // 2. Sleep inside a static initializer
            AtomicBoolean otherRan2 = new AtomicBoolean();
            pinnedClinit = countPinnedEvents(() -> {
                Thread initializer = Thread.ofVirtual().start(SlowInit::touch);
                while (!SLOW_INIT_RUNNING.get()) Thread.onSpinWait();
                Thread other = Thread.ofVirtual().start(() -> otherRan2.set(SLOW_INIT_RUNNING.get()));
                join(initializer);
                join(other);
            });
            ranDuringClinit = otherRan2.get();
        } finally {
            System.setOut(console);
        }

        System.out.println("one carrier; virtual thread A blocks, can virtual thread B run meanwhile?");
        System.out.println("  A sleeps inside synchronized:  " + ranDuringSynchronized + "  (JFR VirtualThreadPinned events: " + pinnedSynchronized + ")");
        System.out.println("  A sleeps inside <clinit>:      " + ranDuringClinit + " (JFR VirtualThreadPinned events: " + pinnedClinit + ")");
        System.out.println("jdk.tracePinnedThreads printed anything: " + (traced.size() > 0));
    }

    static long countPinnedEvents(Runnable scenario) throws Exception {
        Path file = Files.createTempFile("pinned", ".jfr");
        try (Recording r = new Recording()) {
            r.enable("jdk.VirtualThreadPinned").withThreshold(Duration.ZERO);
            r.start();
            scenario.run();
            r.stop();
            r.dump(file);
            return RecordingFile.readAllEvents(file).size();
        } finally {
            Files.deleteIfExists(file);
        }
    }

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static void join(Thread t) {
        try {
            t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

/* Output:
one carrier; virtual thread A blocks, can virtual thread B run meanwhile?
  A sleeps inside synchronized:  true  (JFR VirtualThreadPinned events: 0)
  A sleeps inside <clinit>:      false (JFR VirtualThreadPinned events: 1)
jdk.tracePinnedThreads printed anything: false
*/
