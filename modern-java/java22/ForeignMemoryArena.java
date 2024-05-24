// JEP 454 — Foreign Function & Memory API (Java 22): arenas and segments
//
// Off-heap memory used to mean ByteBuffer.allocateDirect (2 GB limit, freed
// only when the GC gets around to it) or sun.misc.Unsafe (no bounds checks,
// no lifetime checks: one bad offset corrupts the process). The FFM API in
// java.lang.foreign replaces both with a MemorySegment, a bounds-checked
// view of a memory region, whose lifetime is controlled by an Arena. A
// confined arena belongs to one thread and frees everything at close();
// any later access fails instead of reading freed memory. This is the
// spatial and temporal safety that native code does not give you.
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class ForeignMemoryArena {
    public static void main(String[] args) {
        MemorySegment escaped;
        try (Arena arena = Arena.ofConfined()) {
            // 10 ints, off-heap, zero-initialized
            MemorySegment ints = arena.allocate(ValueLayout.JAVA_INT, 10);
            for (int i = 0; i < 10; i++) {
                ints.setAtIndex(ValueLayout.JAVA_INT, i, i * i);
            }
            System.out.println("byteSize: " + ints.byteSize() + ", isNative: " + ints.isNative());
            System.out.println("ints[9] = " + ints.getAtIndex(ValueLayout.JAVA_INT, 9));

            // Spatial safety: index 10 is outside the segment
            try {
                ints.getAtIndex(ValueLayout.JAVA_INT, 10);
            } catch (IndexOutOfBoundsException e) {
                System.out.println("ints[10]: " + e.getClass().getSimpleName());
            }

            // Slices share memory but have their own, tighter bounds
            MemorySegment tail = ints.asSlice(5 * 4L);
            System.out.println("slice size " + tail.byteSize() + ", tail[0] = " + tail.getAtIndex(ValueLayout.JAVA_INT, 0));

            // NUL-terminated C strings, written and read with an explicit charset
            MemorySegment cString = arena.allocateFrom("héllo", StandardCharsets.UTF_8);
            System.out.println("C string bytes incl. NUL: " + cString.byteSize()
                    + ", read back: " + cString.getString(0, StandardCharsets.UTF_8));

            // Copy out to the heap
            int[] copy = ints.asSlice(0, 4 * 4L).toArray(ValueLayout.JAVA_INT);
            System.out.println("first four on heap: " + Arrays.toString(copy));
            escaped = ints;
        } // all memory of the arena is freed here, deterministically

        // Temporal safety: the segment outlived its arena
        System.out.println("escaped segment alive: " + escaped.scope().isAlive());
        try {
            escaped.getAtIndex(ValueLayout.JAVA_INT, 0);
        } catch (IllegalStateException e) {
            System.out.println("use after close: " + e.getClass().getSimpleName());
        }

        // Confined means one thread: another thread may not touch it
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(8);
            Thread t = Thread.ofPlatform().start(() -> {
                try {
                    seg.set(ValueLayout.JAVA_LONG, 0, 1L);
                } catch (WrongThreadException e) {
                    System.out.println("other thread: " + e.getClass().getSimpleName());
                }
            });
            t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Heap segments wrap Java arrays with the same API
        MemorySegment heap = MemorySegment.ofArray(new byte[]{1, 2, 3, 4});
        int bigEndian = heap.get(ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN), 0);
        System.out.println("heap segment isNative: " + heap.isNative()
                + ", big-endian int: 0x" + Integer.toHexString(bigEndian));
    }
}

/* Output:
byteSize: 40, isNative: true
ints[9] = 81
ints[10]: IndexOutOfBoundsException
slice size 20, tail[0] = 25
C string bytes incl. NUL: 7, read back: héllo
first four on heap: [0, 1, 4, 9]
escaped segment alive: false
use after close: IllegalStateException
other thread: WrongThreadException
heap segment isNative: false, big-endian int: 0x1020304
*/
