// Exception tables: how try/catch/finally exist in bytecode
//
// There is no "try" instruction. Each method's Code attribute has an
// exception table of entries (start_pc, end_pc, handler_pc, catch_type):
// if an exception is thrown while pc is in [start, end) and is an instance of
// catch_type, the JVM clears the operand stack, pushes the exception and
// jumps to handler_pc. Entries are searched in order, so inner handlers come
// first; catch_type 0 ("any") catches everything. javac lowers the rest:
//  - multi-catch: one entry per alternative, same handler;
//  - finally: the finally body is COPIED onto every exit path (normal return,
//    each catch) plus an "any" handler that runs it and rethrows (athrow), so
//    a long finally block appears several times in the class file;
//  - try-with-resources: close() on each path and Throwable.addSuppressed when
//    close() fails after the body did;
//  - synchronized blocks: an "any" handler, covering itself, that makes sure
//    monitorexit runs even on exceptions.
// The program compiles a class in memory and prints each method's exception
// table and a few telling instructions from javap.
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

public class ExceptionTable {
    static final String SOURCE = """
            import java.io.*;
            public class Ex {
                static int tryCatch(String s) {
                    try { return Integer.parseInt(s); } catch (NumberFormatException e) { return -1; }
                }
                static int multiCatch(Object o) {
                    try { return ((String) o).length(); }
                    catch (ClassCastException | NullPointerException e) { return -1; }
                }
                static int tryFinally(int[] a) {
                    try { return a[0]; } catch (RuntimeException e) { return -1; } finally { a[1] = 99; }
                }
                static int twr(Reader r) throws IOException {
                    try (r) { return r.read(); }
                }
                static void sync(Object lock, int[] a) {
                    synchronized (lock) { a[0]++; }
                }
            }
            """;

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("extable");
        try {
            Path src = dir.resolve("Ex.java");
            Files.writeString(src, SOURCE);
            tool("javac", "--release", "17", "-d", dir.toString(), src.toString());
            String javap = tool("javap", "-c", "-p", dir.resolve("Ex.class").toString());
            for (String m : List.of("tryCatch", "multiCatch", "tryFinally", "twr", "sync")) {
                System.out.println(m + "():");
                describe(javap, " " + m + "(");
            }
        } finally {
            try (Stream<Path> s = Files.walk(dir)) {
                for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
    }

    static void describe(String javap, String method) {
        boolean in = false, table = false;
        int finallyCopies = 0, athrows = 0, monitorExits = 0, suppressed = 0;
        for (String line : javap.lines().toList()) {
            String t = line.strip();
            if (t.endsWith(");") || t.endsWith("throws java.io.IOException;")) { in = t.contains(method); table = false; continue; }
            if (!in) continue;
            if (t.startsWith("Exception table:")) { table = true; continue; }
            if (table && t.startsWith("from")) { System.out.println("    from  to  target  type"); continue; }
            if (table && t.matches("\\d+\\s+\\d+\\s+\\d+\\s+.*")) {
                String[] f = t.split("\\s+", 4);
                String type = f[3].replace("Class ", "").replace("java/lang/", "");
                System.out.printf("    %4s %3s %7s  %s%n", f[0], f[1], f[2], type);
                continue;
            }
            if (t.contains("bipush        99")) finallyCopies++;
            if (t.matches("\\d+: athrow")) athrows++;
            if (t.matches("\\d+: monitorexit")) monitorExits++;
            if (t.contains("addSuppressed")) suppressed++;
        }
        if (finallyCopies > 0) System.out.println("    copies of the finally body: " + finallyCopies + ", athrow: " + athrows);
        if (suppressed > 0) System.out.println("    calls to addSuppressed: " + suppressed + ", athrow: " + athrows);
        if (monitorExits > 0) System.out.println("    monitorexit instructions: " + monitorExits + ", athrow: " + athrows);
    }

    static String tool(String name, String... args) {
        StringWriter out = new StringWriter();
        int rc = ToolProvider.findFirst(name).orElseThrow().run(new PrintWriter(out), new PrintWriter(out), args);
        if (rc != 0) throw new IllegalStateException(name + " failed:\n" + out);
        return out.toString();
    }
}

/* Output:
tryCatch():
    from  to  target  type
       0   4       5  NumberFormatException
multiCatch():
    from  to  target  type
       0   7       8  ClassCastException
       0   7       8  NullPointerException
tryFinally():
    from  to  target  type
       0   4      11  RuntimeException
       0   4      21  any
      11  14      21  any
    copies of the finally body: 3, athrow: 1
twr():
    from  to  target  type
       2   7      17  Throwable
      22  26      29  Throwable
    calls to addSuppressed: 1, athrow: 1
sync():
    from  to  target  type
       4  13      16  any
      16  19      16  any
    monitorexit instructions: 2, athrow: 1
*/
