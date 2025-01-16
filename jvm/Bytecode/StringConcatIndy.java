// String concatenation in bytecode: StringBuilder chains vs invokedynamic
//
// Up to Java 8, javac compiled a + b + c into new StringBuilder().append(a)
// .append(b).append(c).toString(): the strategy was frozen into every class
// file, and the JIT had to recognise the pattern to optimise it. JEP 280
// (Java 9) replaced it with ONE invokedynamic instruction whose bootstrap
// method is StringConcatFactory.makeConcatWithConstants. The call site gets
// the dynamic values as arguments and a "recipe" string as a static bootstrap
// argument, where \u0001 marks an argument slot and the rest is literal
// text; the JDK picks (and can improve) the concatenation strategy at link
// time, without recompiling anything. Compile-time constants are still folded
// by javac into a single ldc. Below, the same method is compiled with
// --release 8 and with the current release, and javap shows the difference;
// both class files are then loaded and must produce the same string.
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

public class StringConcatIndy {
    static final String SOURCE = """
            public class Concat {
                public static String greet(String name, int n, double d) {
                    return "Hi " + name + ", you have " + n + " items (" + d + ")";
                }
                public static String constant() {
                    return "folded" + '-' + 42 + "-" + true;
                }
            }
            """;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("concat");
        try {
            Path src = root.resolve("Concat.java");
            Files.writeString(src, SOURCE);
            Path out8 = Files.createDirectory(root.resolve("release8"));
            Path outNew = Files.createDirectory(root.resolve("release17"));
            tool("javac", "--release", "8", "-Xlint:-options", "-d", out8.toString(), src.toString());
            tool("javac", "--release", "17", "-d", outNew.toString(), src.toString());

            System.out.println("--release 8, greet():");
            printInstructions(tool("javap", "-c", out8.resolve("Concat.class").toString()), "greet(", "invoke|new ");
            System.out.println("--release 17, greet():");
            String verbose = tool("javap", "-c", "-v", outNew.resolve("Concat.class").toString());
            printInstructions(verbose, "greet(", "invoke|new ");
            // The recipe is a String constant passed as a static argument to the bootstrap method
            verbose.lines().filter(l -> l.contains("= String") && l.contains("\\u0001")).findFirst()
                    .ifPresent(l -> System.out.println("    recipe: \"" + l.substring(l.indexOf("// ") + 3) + "\""));
            System.out.println("--release 17, constant():");
            printInstructions(verbose, "constant(", "ldc|invoke");

            String r8 = call(out8, "greet"), rNew = call(outNew, "greet");
            System.out.println("results equal: " + r8.equals(rNew) + " -> " + rNew);
            System.out.println("constant(): " + call(outNew, "constant"));
        } finally {
            try (Stream<Path> s = Files.walk(root)) {
                for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
    }

    // Print the bytecode lines of one method that match a mnemonic pattern, without constant-pool numbers
    static void printInstructions(String javap, String method, String mnemonics) {
        boolean in = false;
        for (String line : javap.lines().toList()) {
            String t = line.strip();
            if (t.endsWith(");") || t.endsWith("}")) in = t.contains(method);
            else if (in && t.matches("\\d+: (" + mnemonics + ").*")) {
                String clean = t.replaceAll("#\\d+(,\\s+\\d+)?\\s*", "").replaceAll("\\s+//\\s+", " ")
                        .replaceAll("InvokeDynamic \\S*?:", "").replaceAll("(Method|class|String) ", "");
                System.out.println("    " + clean.replaceFirst("^\\d+: ", ""));
            }
        }
    }

    static String call(Path classes, String method) throws Exception {
        try (URLClassLoader loader = new URLClassLoader(new URL[] {classes.toUri().toURL()}, null)) {
            Class<?> c = loader.loadClass("Concat");
            Method m = method.equals("greet") ? c.getMethod("greet", String.class, int.class, double.class) : c.getMethod(method);
            return (String) (method.equals("greet") ? m.invoke(null, "Ada", 3, 2.5) : m.invoke(null));
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
--release 8, greet():
    new java/lang/StringBuilder
    invokespecial java/lang/StringBuilder."<init>":()V
    invokevirtual java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invokevirtual java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invokevirtual java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invokevirtual java/lang/StringBuilder.append:(I)Ljava/lang/StringBuilder;
    invokevirtual java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invokevirtual java/lang/StringBuilder.append:(D)Ljava/lang/StringBuilder;
    invokevirtual java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invokevirtual java/lang/StringBuilder.toString:()Ljava/lang/String;
--release 17, greet():
    invokedynamic makeConcatWithConstants:(Ljava/lang/String;ID)Ljava/lang/String;
    recipe: "Hi \u0001, you have \u0001 items (\u0001)"
--release 17, constant():
    ldc folded-42-true
results equal: true -> Hi Ada, you have 3 items (2.5)
constant(): folded-42-true
*/
