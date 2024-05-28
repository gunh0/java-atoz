// Inside a .class file: a small hand-written parser (JVMS chapter 4)
//
// A class file is a big-endian byte stream: magic 0xCAFEBABE, minor and
// major version (Java 8 = 52, 17 = 61, 21 = 65: feature release + 44), the
// constant pool (every name, descriptor, string literal and numeric
// constant the class refers to, numbered from 1; long and double entries
// take two slots), access flags, this_class / super_class / interfaces as
// indexes into the pool, then fields, methods and attributes, each with
// their own attributes (a method's bytecode lives in its "Code" attribute).
// A JVM refuses a class whose major version is newer than itself
// (UnsupportedClassVersionError), which is what --release protects you
// from. This parser walks the whole structure of a class compiled in
// memory; it ignores the bytecode itself.
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

public class ClassFileHeader {
    static final String SOURCE = """
            package demo;
            public final class Point implements Comparable<Point>, java.io.Serializable {
                public static final long ORIGIN_ID = 1L << 40;
                private final int x, y;
                public Point(int x, int y) { this.x = x; this.y = y; }
                public int compareTo(Point o) { return Integer.compare(x * x + y * y, o.x * o.x + o.y * o.y); }
                public double length() { return Math.sqrt(x * x + y * y); }
            }
            """;

    public static void main(String[] args) throws Exception {
        for (String release : new String[] {"8", "17", "21"}) {
            byte[] b = compile("demo.Point", SOURCE, release).get("demo.Point");
            System.out.printf("--release %-2s -> major version %d%n", release, ((b[6] & 0xff) << 8) | (b[7] & 0xff));
        }
        System.out.println();
        parse(compile("demo.Point", SOURCE, "17").get("demo.Point"));
    }

    static void parse(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        System.out.printf("magic:      %08X%n", in.readInt());
        int minor = in.readUnsignedShort(), major = in.readUnsignedShort();
        System.out.println("version:    " + major + "." + minor + " (Java " + (major - 44) + ")");

        // Constant pool: keep UTF-8 strings and Class -> name index links
        int count = in.readUnsignedShort();
        String[] utf8 = new String[count];
        int[] classNameIndex = new int[count];
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> utf8[i] = in.readUTF();                                   // Utf8
                case 7 -> classNameIndex[i] = in.readUnsignedShort();               // Class
                case 8, 16, 19, 20 -> in.skipBytes(2);                             // String, MethodType, Module, Package
                case 3, 4, 9, 10, 11, 12, 17, 18 -> in.skipBytes(4);               // Integer, Float, refs, NameAndType, (Invoke)Dynamic
                case 15 -> in.skipBytes(3);                                        // MethodHandle
                case 5, 6 -> { in.skipBytes(8); i++; }                             // Long, Double: two slots
                default -> throw new IOException("unknown constant pool tag " + tag);
            }
        }
        int access = in.readUnsignedShort();
        System.out.println("access:     " + flags(access, new String[] {"public", "", "", "", "final", "super", "", "", "", "interface", "abstract"}));
        System.out.println("this:       " + utf8[classNameIndex[in.readUnsignedShort()]]);
        System.out.println("super:      " + utf8[classNameIndex[in.readUnsignedShort()]]);
        List<String> interfaces = new ArrayList<>();
        for (int n = in.readUnsignedShort(); n > 0; n--) interfaces.add(utf8[classNameIndex[in.readUnsignedShort()]]);
        System.out.println("interfaces: " + interfaces);

        // 0x0040 means volatile on a field but bridge on a method
        String[] fieldFlags = {"public", "private", "protected", "static", "final", "", "volatile", "transient", "", "", "", "", "synthetic"};
        String[] methodFlags = {"public", "private", "protected", "static", "final", "synchronized", "bridge", "varargs", "native", "", "abstract", "", "synthetic"};
        for (String kind : new String[] {"field", "method"}) {
            String[] memberFlags = kind.equals("field") ? fieldFlags : methodFlags;
            for (int n = in.readUnsignedShort(); n > 0; n--) {
                int acc = in.readUnsignedShort();
                String name = utf8[in.readUnsignedShort()], desc = utf8[in.readUnsignedShort()];
                List<String> attrs = attributes(in, utf8);
                System.out.printf("%-6s %-10s %-24s %-22s %s%n", kind, name, desc, flags(acc, memberFlags), attrs);
            }
        }
        // The bridge compareTo(Object) was generated by javac for the generic interface
        System.out.println("class attributes: " + attributes(in, utf8));
        System.out.println("parsed to the last byte: " + (in.available() == 0));
    }

    // Reads an attribute table, returning the attribute names (contents skipped)
    static List<String> attributes(DataInputStream in, String[] utf8) throws IOException {
        List<String> names = new ArrayList<>();
        for (int n = in.readUnsignedShort(); n > 0; n--) {
            String name = utf8[in.readUnsignedShort()];
            in.skipBytes(in.readInt());
            if (!name.equals("LineNumberTable")) names.add(name); // debug info, omitted for brevity
        }
        return names;
    }

    static String flags(int value, String[] names) {
        List<String> out = new ArrayList<>();
        for (int bit = 0; bit < names.length; bit++) {
            if ((value & (1 << bit)) != 0 && !names[bit].isEmpty()) out.add(names[bit]);
        }
        return String.join(" ", out);
    }

    static Map<String, byte[]> compile(String className, String source, String release) {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        Map<String, ByteArrayOutputStream> out = new HashMap<>();
        JavaFileManager fm = new ForwardingJavaFileManager<>(javac.getStandardFileManager(null, null, null)) {
            @Override
            public JavaFileObject getJavaFileForOutput(Location location, String name, JavaFileObject.Kind kind, FileObject sibling) {
                return new SimpleJavaFileObject(URI.create("mem:///" + name.replace('.', '/') + kind.extension), kind) {
                    @Override
                    public OutputStream openOutputStream() {
                        return out.computeIfAbsent(name, n -> new ByteArrayOutputStream());
                    }
                };
            }
        };
        JavaFileObject src = new SimpleJavaFileObject(
                URI.create("string:///" + className.replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) { return source; }
        };
        // -Xlint:-options silences the "release 8 is deprecated" style warnings
        if (!javac.getTask(null, fm, null, List.of("--release", release, "-Xlint:-options"), null, List.of(src)).call()) {
            throw new IllegalStateException("compilation failed");
        }
        Map<String, byte[]> result = new HashMap<>();
        out.forEach((name, b) -> result.put(name, b.toByteArray()));
        return result;
    }
}

/* Output:
--release 8  -> major version 52
--release 17 -> major version 61
--release 21 -> major version 65

magic:      CAFEBABE
version:    61.0 (Java 17)
access:     public final super
this:       demo/Point
super:      java/lang/Object
interfaces: [java/lang/Comparable, java/io/Serializable]
field  ORIGIN_ID  J                        public static final    [ConstantValue]
field  x          I                        private final          []
field  y          I                        private final          []
method <init>     (II)V                    public                 [Code]
method compareTo  (Ldemo/Point;)I          public                 [Code]
method length     ()D                      public                 [Code]
method compareTo  (Ljava/lang/Object;)I    public bridge synthetic [Code, MethodParameters]
class attributes: [Signature, SourceFile]
parsed to the last byte: true
*/
