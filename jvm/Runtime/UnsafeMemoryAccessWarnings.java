// output-jdk: 24
// sun.misc.Unsafe memory access: deprecated for removal, now warned about at run time
//
// sun.misc.Unsafe let libraries read and write raw memory, on and off heap,
// with no bounds checks: a wrong offset corrupts the JVM silently. It was
// never a supported API, but serializers, caches and network frameworks
// relied on it for speed. Its supported replacements are VarHandle (Java 9)
// for on-heap field and array access and the Foreign Function & Memory API
// (Java 22) for off-heap memory. JEP 471 (Java 23) deprecated Unsafe's
// memory-access methods for removal; JEP 498 (Java 24) makes the JVM print a
// warning, once, the first time any of them is called. The option
// --sun-misc-unsafe-memory-access=allow|warn|debug|deny controls this: warn
// is the Java 24 default, debug reports every call with a stack trace, and
// deny throws UnsupportedOperationException, the behaviour planned for a
// later release. Run with deny to find out today whether your dependencies
// still need Unsafe. The output is pinned to JDK 24, whose messages it shows.
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

public class UnsafeMemoryAccessWarnings {
    static final String LEGACY = """
            import java.lang.reflect.Field;
            import sun.misc.Unsafe;
            public class Legacy {
                public static void main(String[] args) throws Exception {
                    Field f = Unsafe.class.getDeclaredField("theUnsafe");
                    f.setAccessible(true);
                    Unsafe u = (Unsafe) f.get(null);
                    long address = u.allocateMemory(8);   // four memory-access calls,
                    u.putLong(address, 42L);              // but only the first one is
                    long value = u.getLong(address);      // reported in "warn" mode
                    u.freeMemory(address);
                    System.out.println("Legacy read back " + value);
                }
            }
            """;

    public static void main(String[] args) throws Exception {
        boolean forRemoval = Class.forName("sun.misc.Unsafe").getMethod("allocateMemory", long.class)
                .getAnnotation(Deprecated.class).forRemoval();
        System.out.println("Unsafe.allocateMemory is @Deprecated(forRemoval = true): " + forRemoval);

        Path dir = Files.createTempDirectory("unsafe");
        try {
            Path src = dir.resolve("Legacy.java");
            Files.writeString(src, LEGACY);
            StringWriter log = new StringWriter();
            int rc = ToolProvider.findFirst("javac").orElseThrow()
                    .run(new PrintWriter(log), new PrintWriter(log), "-d", dir.toString(), src.toString());
            if (rc != 0) throw new IllegalStateException(log.toString());
            for (String mode : new String[] {null, "allow", "debug", "deny"}) {
                System.out.println("--- " + (mode == null ? "default" : "--sun-misc-unsafe-memory-access=" + mode));
                List<String> cmd = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString()));
                if (mode != null) cmd.add("--sun-misc-unsafe-memory-access=" + mode);
                cmd.addAll(List.of("-cp", dir.toString(), "Legacy"));
                Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes()).replaceAll("\\(file:[^)]*\\)", "(<temp dir>)");
                out.lines().filter(l -> !l.startsWith("\tat jdk.unsupported/")).forEach(System.out::println);
                System.out.println("exit code " + p.waitFor());
            }
        } finally {
            try (Stream<Path> s = Files.walk(dir)) {
                for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }

        // The supported way to do the same: bounds-checked, freed when the arena closes
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(ValueLayout.JAVA_LONG);
            segment.set(ValueLayout.JAVA_LONG, 0, 42L);
            System.out.println("FFM read back " + segment.get(ValueLayout.JAVA_LONG, 0));
            try {
                segment.get(ValueLayout.JAVA_LONG, 8); // one long past the end
            } catch (IndexOutOfBoundsException e) {
                System.out.println("FFM out-of-bounds read: IndexOutOfBoundsException");
            }
        }
    }
}

/* Output:
Unsafe.allocateMemory is @Deprecated(forRemoval = true): true
--- default
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::allocateMemory has been called by Legacy (<temp dir>)
WARNING: Please consider reporting this to the maintainers of class Legacy
WARNING: sun.misc.Unsafe::allocateMemory will be removed in a future release
Legacy read back 42
exit code 0
--- --sun-misc-unsafe-memory-access=allow
Legacy read back 42
exit code 0
--- --sun-misc-unsafe-memory-access=debug
WARNING: sun.misc.Unsafe::allocateMemory called by Legacy (<temp dir>)
	at Legacy.main(Legacy.java:8)
WARNING: sun.misc.Unsafe::putLong called by Legacy (<temp dir>)
	at Legacy.main(Legacy.java:9)
WARNING: sun.misc.Unsafe::getLong called by Legacy (<temp dir>)
	at Legacy.main(Legacy.java:10)
WARNING: sun.misc.Unsafe::freeMemory called by Legacy (<temp dir>)
	at Legacy.main(Legacy.java:11)
Legacy read back 42
exit code 0
--- --sun-misc-unsafe-memory-access=deny
Exception in thread "main" java.lang.UnsupportedOperationException: allocateMemory
	at Legacy.main(Legacy.java:8)
exit code 1
FFM read back 42
FFM out-of-bounds read: IndexOutOfBoundsException
*/
