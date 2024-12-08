// Escape analysis: when `new` does not allocate
//
// HotSpot first interprets a method, then compiles it when it gets hot (C1,
// then the optimizing C2). C2 runs escape analysis: if an object never
// escapes the compiled code (not stored in a field or array, not returned,
// not passed to a method that is not inlined), it can be replaced by its
// fields in registers ("scalar replacement") and the allocation disappears.
// So small temporary objects (points, iterators, builders, boxed values) are
// often free in hot code, and "avoid allocating" micro-optimizations are
// often pointless. None of this is guaranteed: it depends on inlining
// budgets, the compiler, flags (-XX:-DoEscapeAnalysis) and timing. Measure
// real code with a harness such as JMH; naive System.nanoTime loops mostly
// measure JIT warm-up. This program prints no timings: it watches the bytes
// allocated per call (HotSpot's ThreadMXBean extension) while the code warms
// up, and reports only verdicts.
import java.lang.management.ManagementFactory;

public class EscapeAnalysis {
    record Point(int x, int y) {
        int lengthSquared() { return x * x + y * y; }
    }

    static Point lastEscaped; // storing into a static field = escaping

    // Point never leaves this method: a candidate for scalar replacement
    static int noEscape(int i) {
        Point p = new Point(i, i + 1);
        return p.lengthSquared();
    }

    // Point is published: it must really be allocated
    static int escapes(int i) {
        Point p = new Point(i, i + 1);
        lastEscaped = p;
        return p.lengthSquared();
    }

    interface Kernel { int run(int i); }

    static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    static long checksum;

    // Bytes allocated by one batch of calls
    static long batch(Kernel k, int calls) {
        long before = THREADS.getCurrentThreadAllocatedBytes();
        int sum = 0;
        for (int i = 0; i < calls; i++) sum += k.run(i);
        checksum += sum;
        return THREADS.getCurrentThreadAllocatedBytes() - before;
    }

    public static void main(String[] args) {
        // A short first batch runs before C2 has compiled anything
        long firstNoEscape = batch(EscapeAnalysis::noEscape, 1_000);
        long firstEscapes = batch(EscapeAnalysis::escapes, 1_000);
        System.out.println("first 1,000 calls (not yet optimized): both allocate a Point per call: "
                + (firstNoEscape >= 1_000 * 16L && firstEscapes >= 1_000 * 16L));

        // Warm up until compiled code stops allocating (bounded: about 3 seconds)
        long deadline = System.nanoTime() + 3_000_000_000L;
        long lastNoEscape = Long.MAX_VALUE, lastEscapes = 0;
        while (System.nanoTime() < deadline && lastNoEscape > 1_000) {
            lastNoEscape = batch(EscapeAnalysis::noEscape, 100_000);
            lastEscapes = batch(EscapeAnalysis::escapes, 100_000);
        }
        System.out.println("after warm-up, 100,000 calls, non-escaping Point: allocation eliminated: " + (lastNoEscape <= 1_000));
        System.out.println("after warm-up, 100,000 calls, escaping Point: still allocated every call: " + (lastEscapes >= 100_000 * 16L));
        System.out.println("results identical either way: " + (noEscape(7) == escapes(7)));
    }
}

/* Output:
first 1,000 calls (not yet optimized): both allocate a Point per call: true
after warm-up, 100,000 calls, non-escaping Point: allocation eliminated: true
after warm-up, 100,000 calls, escaping Point: still allocated every call: true
results identical either way: true
*/
