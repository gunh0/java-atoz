// Records and sealed types in the class file
//
// Neither feature added a bytecode instruction; both are class-file
// metadata plus javac-generated code.
// A record compiles to a final class extending java.lang.Record, with private
// final fields, a canonical constructor and accessor methods, plus a Record
// attribute listing the components (name and descriptor), which is what
// Class.getRecordComponents() and serialization read. toString, hashCode and
// equals are not written out as code: each is one invokedynamic whose
// bootstrap method, java.lang.runtime.ObjectMethods.bootstrap, receives the
// record class, the component names ("x;y") and getter method handles for
// the fields, and builds the implementation at link time.
// A sealed class or interface carries a PermittedSubclasses attribute (there
// is no "sealed" access flag); the JVM enforces it when a subclass is loaded,
// so a class not in the list cannot extend it even if javac never saw it.
// The program compiles the types in memory and prints the relevant parts of
// javap -v, then the reflective view.
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

public class RecordsAndSealedBytecode {
    static final String SOURCE = """
            public record Point(int x, int y) {}
            sealed interface Shape permits Circle, Square {}
            record Circle(double r) implements Shape {}
            final class Square implements Shape {}
            """;

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("records");
        try {
            Path src = dir.resolve("Point.java");
            Files.writeString(src, SOURCE);
            tool("javac", "--release", "17", "-d", dir.toString(), src.toString());

            String point = tool("javap", "-v", "-p", dir.resolve("Point.class").toString());
            List<String> lines = point.lines().toList();
            System.out.println(lines.stream().filter(l -> l.contains("class Point extends")).findFirst().orElseThrow());
            lines.stream().map(String::strip).filter(l -> l.startsWith("flags:")).findFirst()
                    .ifPresent(l -> System.out.println("  " + l));
            System.out.println("fields: " + lines.stream().map(String::strip).filter(l -> l.startsWith("private final")).toList());
            System.out.println("methods:");
            lines.stream().filter(l -> l.startsWith("  public") && l.endsWith(");"))
                    .forEach(l -> System.out.println("  " + l.strip()));
            System.out.println("invokedynamic in the generated methods:");
            lines.stream().map(String::strip).filter(l -> l.contains(": invokedynamic"))
                    .map(l -> l.substring(l.indexOf("// ") + 3).replaceFirst("InvokeDynamic #\\d+:", ""))
                    .forEach(l -> System.out.println("  " + l));
            section(lines, "Record:", "BootstrapMethods:");
            section(lines, "BootstrapMethods:", "InnerClasses:");

            String shape = tool("javap", "-v", dir.resolve("Shape.class").toString());
            List<String> shapeLines = shape.lines().toList();
            System.out.println(shapeLines.stream().filter(l -> l.contains("interface Shape")).findFirst().orElseThrow().strip());
            shapeLines.stream().map(String::strip).filter(l -> l.startsWith("flags:")).findFirst()
                    .ifPresent(l -> System.out.println("  " + l + "   <- no 'sealed' flag"));
            section(shapeLines, "PermittedSubclasses:", null);

            try (URLClassLoader loader = new URLClassLoader(new URL[] {dir.toUri().toURL()}, null)) {
                Class<?> p = loader.loadClass("Point");
                Class<?> s = loader.loadClass("Shape");
                System.out.println("reflection: Point.isRecord() " + p.isRecord() + ", components "
                        + Arrays.stream(p.getRecordComponents()).map(c -> c.getType() + " " + c.getName()).toList());
                System.out.println("reflection: Shape.isSealed() " + s.isSealed() + ", permitted "
                        + Arrays.stream(s.getPermittedSubclasses()).map(Class::getName).toList());
                Object pt = p.getConstructor(int.class, int.class).newInstance(3, 4);
                System.out.println("generated toString: " + pt + ", equals a new Point(3, 4): " + pt.equals(p.getConstructor(int.class, int.class).newInstance(3, 4)));
            }
        } finally {
            try (Stream<Path> st = Files.walk(dir)) {
                for (Path f : st.sorted(Comparator.reverseOrder()).toList()) Files.delete(f);
            }
        }
    }

    // Print a javap section from its header up to the next header, without constant-pool numbers
    static void section(List<String> lines, String start, String end) {
        boolean in = false;
        for (String l : lines) {
            if (l.startsWith(start)) { in = true; System.out.println(start); continue; }
            if (in && (end != null && l.startsWith(end) || !l.startsWith(" ") && !l.isBlank())) break;
            if (in && !l.isBlank()) {
                String t = l.replaceAll("#\\d+\\s*", "").replace("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/TypeDescriptor;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;", "(Lookup, String, TypeDescriptor, Class, String, MethodHandle[])");
                System.out.println(t);
            }
        }
    }

    static String tool(String name, String... args) {
        StringWriter out = new StringWriter();
        int rc = ToolProvider.findFirst(name).orElseThrow().run(new PrintWriter(out), new PrintWriter(out), args);
        if (rc != 0) throw new IllegalStateException(name + " failed:\n" + out);
        return out.toString();
    }
}

/* Output:
public final class Point extends java.lang.Record
  flags: (0x0031) ACC_PUBLIC, ACC_FINAL, ACC_SUPER
fields: [private final int x;, private final int y;]
methods:
  public Point(int, int);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public int x();
  public int y();
invokedynamic in the generated methods:
  toString:(LPoint;)Ljava/lang/String;
  hashCode:(LPoint;)I
  equals:(LPoint;Ljava/lang/Object;)Z
Record:
  int x;
    descriptor: I
  int y;
    descriptor: I
BootstrapMethods:
  0: REF_invokeStatic java/lang/runtime/ObjectMethods.bootstrap:(Lookup, String, TypeDescriptor, Class, String, MethodHandle[])
    Method arguments:
      Point
      x;y
      REF_getField Point.x:I
      REF_getField Point.y:I
interface Shape
  flags: (0x0600) ACC_INTERFACE, ACC_ABSTRACT   <- no 'sealed' flag
PermittedSubclasses:
  Circle
  Square
reflection: Point.isRecord() true, components [int x, int y]
reflection: Shape.isSealed() true, permitted [Circle, Square]
generated toString: Point[x=3, y=4], equals a new Point(3, 4): true
*/
