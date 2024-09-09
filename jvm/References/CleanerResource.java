// java.lang.ref.Cleaner: a safety net for native resources, not a destructor
//
// Finalizers are deprecated for removal: they run late or never, on an
// unknown thread, and can resurrect objects (see effective/08). Cleaner
// (Java 9) is the replacement built on PhantomReference: you register an
// object together with a cleanup ACTION, and when the object becomes phantom
// reachable a Cleaner thread runs the action, exactly once. The action must
// not refer to the object it cleans (no lambda capturing `this`, no inner
// class): otherwise the Cleaner itself keeps the object reachable and the
// action never runs. Keep the state to release in a separate static class.
// The primary path stays explicit: implement AutoCloseable, call
// cleanable.clean() in close(), use try-with-resources; the Cleaner only
// catches objects someone forgot to close. GC is triggered in a bounded loop.
import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicInteger;

public class CleanerResource {
    static final Cleaner CLEANER = Cleaner.create();
    static final AtomicInteger released = new AtomicInteger();

    // Correct: the action is a static nested class holding only the handle
    static final class NativeBuffer implements AutoCloseable {
        private static final class Release implements Runnable {
            private final long handle;
            Release(long handle) { this.handle = handle; }
            @Override public void run() { release(handle); }
        }

        private final Cleaner.Cleanable cleanable;

        NativeBuffer(long handle) {
            cleanable = CLEANER.register(this, new Release(handle));
        }

        @Override public void close() { cleanable.clean(); } // runs the action at most once
    }

    // Broken: the action is a lambda that captures `this`
    static final class LeakyBuffer {
        final long handle;
        LeakyBuffer(long handle) {
            this.handle = handle;
            CLEANER.register(this, () -> release(this.handle)); // captures `this`
        }
    }

    static void release(long handle) { released.incrementAndGet(); } // free(handle) in real code

    public static void main(String[] args) throws Exception {
        // 1. Explicit close: action runs immediately, and only once
        try (NativeBuffer b = new NativeBuffer(1)) {
            System.out.println("in use, released so far: " + released.get());
        }
        System.out.println("after try-with-resources: " + released.get());

        released.set(0);
        NativeBuffer twice = new NativeBuffer(2);
        twice.close();
        twice.close();
        System.out.println("close() called twice, action ran: " + released.get() + " time");

        // 2. Forgotten close: the Cleaner catches it after GC
        released.set(0);
        new NativeBuffer(3); // never closed, immediately unreachable
        System.out.println("forgotten buffer released by the Cleaner: " + gcUntil(() -> released.get() == 1));

        // 3. Action captures `this`: the object can never become unreachable
        released.set(0);
        new LeakyBuffer(4);
        System.out.println("leaky buffer ever released: " + gcUntil(() -> released.get() > 0));
    }

    interface Condition { boolean holds(); }

    static boolean gcUntil(Condition c) throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            if (c.holds()) return true;
            System.gc();
            Thread.sleep(50);
        }
        return c.holds();
    }
}

/* Output:
in use, released so far: 0
after try-with-resources: 1
close() called twice, action ran: 1 time
forgotten buffer released by the Cleaner: true
leaky buffer ever released: false
*/
