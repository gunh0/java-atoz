// JEP 454 — Foreign Function & Memory API (Java 22): calling C functions
//
// Calling a C library from Java used to require JNI: a C stub per function,
// generated headers, a native build for every platform. With the FFM API a
// Linker looks up a symbol (here in the C standard library), and a
// FunctionDescriptor turns it into a MethodHandle you call like any other
// (a "downcall"). Java code can also be passed to C as a function pointer
// (an "upcall"), as with the comparator of qsort below. Calling native
// code can crash the JVM, so these are "restricted" methods: the JVM warns
// on first use unless native access was granted explicitly with
// --enable-native-access. This program checks that first and, if needed,
// runs itself again in a child JVM with the flag.
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class ForeignFunctionCalls {
    static final Linker LINKER = Linker.nativeLinker();
    static final SymbolLookup LIBC = LINKER.defaultLookup();

    public static void main(String[] args) throws Throwable {
        if (!ForeignFunctionCalls.class.getModule().isNativeAccessEnabled()) {
            System.out.println("native access not granted; re-running with --enable-native-access=ALL-UNNAMED");
            String java = ProcessHandle.current().info().command().orElse("java");
            Process child = new ProcessBuilder(List.of(java, "--enable-native-access=ALL-UNNAMED",
                    "ForeignFunctionCalls.java")).redirectErrorStream(true).start();
            System.out.print(new String(child.getInputStream().readAllBytes()));
            System.out.println("child exit code " + child.waitFor());
            return;
        }

        Optional<MemorySegment> strlenAddr = LIBC.find("strlen");
        Optional<MemorySegment> qsortAddr = LIBC.find("qsort");
        if (strlenAddr.isEmpty() || qsortAddr.isEmpty()) {
            // Deterministic fallback for platforms without these symbols
            System.out.println("strlen/qsort not found in the default lookup; nothing to call");
            return;
        }

        // size_t strlen(const char *s);
        MethodHandle strlen = LINKER.downcallHandle(strlenAddr.get(),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

        // void qsort(void *base, size_t n, size_t size, int (*cmp)(const void *, const void *));
        MethodHandle qsort = LINKER.downcallHandle(qsortAddr.get(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                        ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

        try (Arena arena = Arena.ofConfined()) {
            for (String s : new String[]{"hello", "", "naïve"}) {
                MemorySegment cString = arena.allocateFrom(s); // UTF-8, NUL-terminated
                long len = (long) strlen.invokeExact(cString);
                System.out.println("strlen(\"" + s + "\") = " + len + " (Java length " + s.length() + ")");
            }

            // The comparator C will call back into: int compare(const int *a, const int *b)
            // The pointers arrive as zero-length segments, so give them the int's size.
            MethodHandle compareJava = MethodHandles.lookup().findStatic(ForeignFunctionCalls.class,
                    "compareInts", MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class));
            ValueLayout.OfInt intPointee = ValueLayout.JAVA_INT;
            FunctionDescriptor cmpDesc = FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS.withTargetLayout(intPointee),
                    ValueLayout.ADDRESS.withTargetLayout(intPointee));
            MemorySegment comparator = LINKER.upcallStub(compareJava, cmpDesc, arena);

            int[] data = {42, -7, 19, 0, 3, 1000, -7};
            MemorySegment array = arena.allocateFrom(ValueLayout.JAVA_INT, data);
            qsort.invokeExact(array, (long) data.length, ValueLayout.JAVA_INT.byteSize(), comparator);
            System.out.println("qsort with a Java comparator: "
                    + Arrays.toString(array.toArray(ValueLayout.JAVA_INT)));
            System.out.println("comparator called from C: " + (calls > 0));
        }
    }

    static int calls;

    static int compareInts(MemorySegment a, MemorySegment b) {
        calls++;
        int x = a.get(ValueLayout.JAVA_INT, 0);
        int y = b.get(ValueLayout.JAVA_INT, 0);
        return Integer.compare(x, y);
    }
}

/* Output:
native access not granted; re-running with --enable-native-access=ALL-UNNAMED
strlen("hello") = 5 (Java length 5)
strlen("") = 0 (Java length 0)
strlen("naïve") = 6 (Java length 5)
qsort with a Java comparator: [-7, -7, 0, 3, 19, 42, 1000]
comparator called from C: true
child exit code 0
*/
