// Effective Java, Item 83: Use lazy initialization judiciously.
//
// Lazy initialization delays computing a field until it is first needed.
// It saves work only if the field is often unused and costly to create;
// otherwise it makes every access slower and, with multiple threads, adds
// synchronization that is easy to get wrong. Normal (eager) initialization
// is preferable most of the time. When you do need it:
//   - static field: the lazy initialization holder class idiom (the JVM
//     initializes the holder on first use, with no locking afterwards);
//   - instance field: the double-check idiom on a volatile field;
//   - if repeated initialization is harmless: the single-check idiom.
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class LazyInitialization {
    static final AtomicInteger staticInits = new AtomicInteger();
    static final AtomicInteger instanceInits = new AtomicInteger();

    static String expensive(AtomicInteger counter) {
        counter.incrementAndGet();
        return "computed";
    }

    // Holder class idiom for a static field
    private static class FieldHolder {
        static final String field = expensive(staticInits);
    }

    static String getStaticField() {
        return FieldHolder.field;
    }

    // Double-check idiom for an instance field
    private volatile String field;

    String getField() {
        String result = field; // read the volatile field only once on the fast path
        if (result == null) {
            synchronized (this) {
                result = field;
                if (result == null) {
                    field = result = expensive(instanceInits);
                }
            }
        }
        return result;
    }

    // Broken lazy init: no synchronization, may compute more than once
    private String racyField;
    static final AtomicInteger racyInits = new AtomicInteger();

    String getRacyField() {
        if (racyField == null) {
            racyField = expensive(racyInits);
        }
        return racyField;
    }

    public static void main(String[] args) {
        System.out.println("before first use: static inits = " + staticInits.get());

        LazyInitialization obj = new LazyInitialization();
        try (ExecutorService exec = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 1000; i++) {
                exec.execute(() -> {
                    getStaticField();
                    obj.getField();
                    obj.getRacyField();
                });
            }
        }
        System.out.println("holder class idiom: initialized " + staticInits.get() + " time(s)");
        System.out.println("double-check idiom: initialized " + instanceInits.get() + " time(s)");
        System.out.println("unsynchronized:     initialized >= 1 time(s): " + (racyInits.get() >= 1)
                + " (exact count depends on scheduling)");
    }
}

/* Output:
before first use: static inits = 0
holder class idiom: initialized 1 time(s)
double-check idiom: initialized 1 time(s)
unsynchronized:     initialized >= 1 time(s): true (exact count depends on scheduling)
*/
