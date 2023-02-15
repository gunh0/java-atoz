// JEP 193 — Variable Handles (Java 9)
//
// Lock-free code needs atomic operations (compare-and-set, get-and-add) and
// explicit memory ordering on ordinary fields and array elements. Before
// Java 9 the choices were AtomicXxxFieldUpdater (reflective, slow-ish,
// fields only, awkward generics), wrapping every value in an AtomicXxx
// object (an extra allocation and indirection per field), or the internal
// sun.misc.Unsafe. A VarHandle is a typed, checked reference to a field,
// static field or array element that offers the full set of access modes:
// plain get/set, volatile, acquire/release, opaque, compareAndSet,
// getAndAdd, getAndUpdate-style loops, and so on.
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class VarHandles {
    // Before: a reflective field updater, bound to the field by name
    static class OldCounter {
        volatile int hits;
        static final AtomicIntegerFieldUpdater<OldCounter> HITS =
                AtomicIntegerFieldUpdater.newUpdater(OldCounter.class, "hits");
    }

    // After: a VarHandle for the same kind of field
    static class Counter {
        volatile int hits;
        private static final VarHandle HITS;

        static {
            try {
                HITS = MethodHandles.lookup().findVarHandle(Counter.class, "hits", int.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        void hit() {
            HITS.getAndAdd(this, 1);
        }

        boolean resetIf(int expected) {
            return HITS.compareAndSet(this, expected, 0);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        OldCounter old = new OldCounter();
        Counter counter = new Counter();

        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
            Thread thread = new Thread(() -> {
                for (int i = 0; i < 10_000; i++) {
                    OldCounter.HITS.incrementAndGet(old);
                    counter.hit();
                }
            });
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        System.out.println("before (field updater): " + old.hits);
        System.out.println("after  (VarHandle):     " + counter.hits);

        System.out.println("CAS with wrong expected value: " + counter.resetIf(1));
        System.out.println("CAS with right expected value: " + counter.resetIf(40_000) + ", hits = " + counter.hits);

        // Array elements: atomic operations on a plain int[] without AtomicIntegerArray
        VarHandle slots = MethodHandles.arrayElementVarHandle(int[].class);
        int[] histogram = new int[3];
        slots.getAndAdd(histogram, 1, 5);
        slots.compareAndSet(histogram, 2, 0, 7);
        slots.setRelease(histogram, 0, 9);  // release store ...
        int seen = (int) slots.getAcquire(histogram, 0); // ... paired with an acquire load
        System.out.println("array via VarHandle: " + java.util.Arrays.toString(histogram) + ", acquired " + seen);

        // Handles are typed and checked: the wrong type fails fast instead of corrupting memory
        try {
            Object notAnInt = "42";
            Counter.HITS.set(counter, notAnInt);
        } catch (ClassCastException e) {
            System.out.println("wrong value type: " + e.getClass().getSimpleName());
        }
        System.out.println("access modes include compareAndExchange: "
                + slots.isAccessModeSupported(VarHandle.AccessMode.COMPARE_AND_EXCHANGE));
    }
}

/* Output:
before (field updater): 40000
after  (VarHandle):     40000
CAS with wrong expected value: false
CAS with right expected value: true, hits = 0
array via VarHandle: [9, 5, 7], acquired 9
wrong value type: ClassCastException
access modes include compareAndExchange: true
*/
