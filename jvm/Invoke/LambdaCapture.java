// Capturing vs non-capturing lambdas: descriptors, instances, allocation
//
// Invoke/LambdaMetafactoryDemo.java shows how invokedynamic turns a lambda
// into an object. What it costs depends on what the lambda captures. The
// invokedynamic descriptor lists the captured values as parameters:
// ()Supplier for a lambda that captures nothing, (I)IntSupplier for one that
// captures an int, (LCounter;)... for one that touches an instance field or
// method and therefore captures `this`. A non-capturing lambda is linked to a
// constant call site and HotSpot hands out one shared instance; a capturing
// one allocates a new object per evaluation that holds the captured values in
// fields. The `this` capture is the subtle one: a lambda stored in a cache or
// listener list keeps its whole enclosing object reachable. The allocation
// counts come from the per-thread allocation counter of the HotSpot
// ThreadMXBean; the lambdas escape into an array so the JIT cannot
// scalar-replace them, and only a threshold verdict is printed.
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

public class LambdaCapture {
    static final String SOURCE = """
            import java.util.function.*;
            public class Counter {
                int count = 1;
                Supplier<String> none()           { return () -> "constant"; }
                IntSupplier local(int base)       { return () -> base + 1; }
                IntSupplier field()               { return () -> count; }        // reads this.count
                Function<String, Integer> unbound() { return String::length; }
                Supplier<Integer> bound(String s) { return s::length; }
            }
            """;

    static final Object[] SINK = new Object[1]; // makes every lambda escape

    int count = 7;

    Supplier<String> nonCapturing() { return () -> "constant"; }
    IntSupplier capturingLocal(int base) { return () -> base + 1; }
    IntSupplier capturingThis() { return () -> count; }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("capture");
        try {
            Path src = dir.resolve("Counter.java");
            Files.writeString(src, SOURCE);
            tool("javac", "--release", "17", "-d", dir.toString(), src.toString());
            String javap = tool("javap", "-c", "-p", dir.resolve("Counter.class").toString());
            System.out.println("invokedynamic call sites (captured values = descriptor parameters):");
            javap.lines().map(String::strip).filter(l -> l.matches("\\d+: invokedynamic.*"))
                    .map(l -> l.substring(l.indexOf("// ") + 3).replaceFirst("InvokeDynamic #\\d+:", ""))
                    .forEach(l -> System.out.println("  " + l));
            System.out.println("synthetic bodies:");
            javap.lines().map(String::strip).filter(l -> l.contains("lambda$"))
                    .map(l -> l.replaceAll("\\$\\d+\\(", "\\$n(")) // the numbering differs between javac versions
                    .sorted().forEach(l -> System.out.println("  " + l));
        } finally {
            try (Stream<Path> s = Files.walk(dir)) {
                for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }

        LambdaCapture outer = new LambdaCapture();
        System.out.println("non-capturing, same instance twice:   " + (outer.nonCapturing() == outer.nonCapturing()));
        System.out.println("capturing a local, same instance:     " + (outer.capturingLocal(1) == outer.capturingLocal(1)));
        System.out.println("fields of the capturing-this lambda:  " + fieldTypes(outer.capturingThis()));
        System.out.println("fields of the capturing-local lambda: " + fieldTypes(outer.capturingLocal(1)));
        System.out.println("fields of the non-capturing lambda:   " + fieldTypes(outer.nonCapturing()));

        var mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long tid = Thread.currentThread().threadId();
        for (int round = 0; round < 3; round++) { // warm up so the JIT has compiled the loops
            long a0 = mx.getThreadAllocatedBytes(tid);
            for (int i = 0; i < 1_000_000; i++) SINK[0] = outer.nonCapturing();
            long a1 = mx.getThreadAllocatedBytes(tid);
            for (int i = 0; i < 1_000_000; i++) SINK[0] = outer.capturingLocal(i);
            long a2 = mx.getThreadAllocatedBytes(tid);
            if (round == 2) {
                System.out.println("1M non-capturing evaluations allocate < 1 MB: " + (a1 - a0 < 1_000_000));
                System.out.println("1M capturing evaluations allocate > 8 MB:    " + (a2 - a1 > 8_000_000));
            }
        }
    }

    static String fieldTypes(Object lambda) {
        return Arrays.toString(Arrays.stream(lambda.getClass().getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .map(Field::getType).map(Class::getSimpleName).sorted().toArray());
    }

    static String tool(String name, String... args) {
        StringWriter out = new StringWriter();
        int rc = ToolProvider.findFirst(name).orElseThrow().run(new PrintWriter(out), new PrintWriter(out), args);
        if (rc != 0) throw new IllegalStateException(name + " failed:\n" + out);
        return out.toString();
    }
}

/* Output:
invokedynamic call sites (captured values = descriptor parameters):
  get:()Ljava/util/function/Supplier;
  getAsInt:(I)Ljava/util/function/IntSupplier;
  getAsInt:(LCounter;)Ljava/util/function/IntSupplier;
  apply:()Ljava/util/function/Function;
  get:(Ljava/lang/String;)Ljava/util/function/Supplier;
synthetic bodies:
  private int lambda$field$n();
  private static int lambda$local$n(int);
  private static java.lang.String lambda$none$n();
non-capturing, same instance twice:   true
capturing a local, same instance:     false
fields of the capturing-this lambda:  [LambdaCapture]
fields of the capturing-local lambda: [int]
fields of the non-capturing lambda:   []
1M non-capturing evaluations allocate < 1 MB: true
1M capturing evaluations allocate > 8 MB:    true
*/
