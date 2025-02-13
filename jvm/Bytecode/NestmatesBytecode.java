// Nestmates in the class file: from access$000 bridges to NestHost/NestMembers
//
// The language lets an inner class use its outer class's private members,
// but before Java 11 the JVM knew nothing about nesting: Outer and
// Outer$Inner were two unrelated classes, and a private member was private
// to exactly one of them. javac bridged the gap with synthetic,
// package-private static methods (access$000, access$100, ...) that read the
// field or call the method on the inner class's behalf. They bloat the class
// and, being package-private, are callable by any class in the package, a
// hole in "private". JEP 181 (Java 11) gave the JVM the concept: the outer
// class carries a NestMembers attribute listing its nested classes, each
// nested class a NestHost attribute naming the outer one, and the JVM grants
// private access within a nest, so the bridges disappear. The program
// compiles the same source for --release 10 and --release 17 and compares
// the class files with javap and reflection.
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

public class NestmatesBytecode {
    static final String SOURCE = """
            public class Outer {
                private int secret = 42;
                private static String hidden() { return "hidden"; }
                public class Inner {
                    public int peek() { return secret; }            // reads Outer's private field
                    public String call() { return hidden(); }       // calls Outer's private method
                }
                public int askInner() { return new Inner().peek(); }
            }
            """;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("nest");
        try {
            Path src = root.resolve("Outer.java");
            Files.writeString(src, SOURCE);
            for (String release : new String[] {"10", "17"}) {
                Path out = Files.createDirectory(root.resolve("r" + release));
                tool("javac", "--release", release, "-Xlint:-options", "-d", out.toString(), src.toString());
                System.out.println("--release " + release);
                String outer = tool("javap", "-p", "-v", out.resolve("Outer.class").toString());
                String inner = tool("javap", "-p", "-v", out.resolve("Outer$Inner.class").toString());
                System.out.println("  Outer NestMembers: " + attribute(outer, "NestMembers:"));
                System.out.println("  Inner NestHost:    " + attribute(inner, "NestHost:"));

                try (URLClassLoader loader = new URLClassLoader(new URL[] {out.toUri().toURL()}, null)) {
                    Class<?> outerClass = loader.loadClass("Outer");
                    Method[] synthetic = Arrays.stream(outerClass.getDeclaredMethods())
                            .filter(Method::isSynthetic).sorted(Comparator.comparing(Method::getName)).toArray(Method[]::new);
                    System.out.println("  synthetic methods in Outer: " + synthetic.length);
                    for (Method m : synthetic) {
                        String access = Modifier.isPrivate(m.getModifiers()) ? "private" : "package-private";
                        System.out.println("    " + access + " static " + m.getName()
                                + Arrays.toString(Arrays.stream(m.getParameterTypes()).map(Class::getSimpleName).toArray()));
                    }
                    Class<?> innerClass = loader.loadClass("Outer$Inner");
                    System.out.println("  JVM sees Inner as nestmate of Outer: " + innerClass.isNestmateOf(outerClass));
                    Object o = outerClass.getConstructor().newInstance();
                    System.out.println("  askInner() = " + outerClass.getMethod("askInner").invoke(o));
                }
            }
        } finally {
            try (Stream<Path> s = Files.walk(root)) {
                for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
    }

    // The line after "NestMembers:" / the text after "NestHost:", without constant-pool numbers
    static String attribute(String javap, String name) {
        var lines = javap.lines().map(String::strip).toList();
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (l.startsWith(name)) {
                String value = l.length() > name.length() ? l.substring(name.length()) : lines.get(i + 1);
                return value.replaceAll("#\\d+\\s*", "").replaceAll("//\\s*", "").replace("class ", "").strip();
            }
        }
        return "(none)";
    }

    static String tool(String name, String... args) {
        StringWriter out = new StringWriter();
        int rc = ToolProvider.findFirst(name).orElseThrow().run(new PrintWriter(out), new PrintWriter(out), args);
        if (rc != 0) throw new IllegalStateException(name + " failed:\n" + out);
        return out.toString();
    }
}

/* Output:
--release 10
  Outer NestMembers: (none)
  Inner NestHost:    (none)
  synthetic methods in Outer: 2
    package-private static access$000[Outer]
    package-private static access$100[]
  JVM sees Inner as nestmate of Outer: false
  askInner() = 42
--release 17
  Outer NestMembers: Outer$Inner
  Inner NestHost:    Outer
  synthetic methods in Outer: 0
  JVM sees Inner as nestmate of Outer: true
  askInner() = 42
*/
