// Reading bytecode with javap, run in-process through ToolProvider
//
// javap is the JDK's class-file disassembler: `javap -c -p Foo.class` prints
// each method's bytecode. The JVM is a stack machine: instructions push
// locals and constants onto an operand stack (iload_1, iconst_2), operate on
// the top values (iadd, imul), and store or return them (istore, ireturn).
// Local variable slot 0 is `this` in instance methods. Since Java 9 the
// command-line tools are also available as java.util.spi.ToolProvider, so
// a program can run javap (or javac, jar, jlink) without starting a process.
// Reading javap output is how you check what the compiler really generated:
// string concatenation compiles to an invokedynamic call
// (makeConcatWithConstants), a switch on strings to hashCode plus
// lookupswitch, and so on. Constant-pool numbers (#7) vary between
// compilers, so they are stripped from the output below.
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.spi.ToolProvider;

public class JavapBytecode {
    static final String SOURCE = """
            public class Calc {
                int add(int a, int b) { return a + b; }
                int max(int a, int b) { return a > b ? a : b; }
                String greet(String name) { return "Hi " + name; }
                static long square(long x) { return x * x; }
            }
            """;

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("javap-demo");
        Path src = dir.resolve("Calc.java");
        try {
            Files.writeString(src, SOURCE);
            run("javac", "--release", "17", "-d", dir.toString(), src.toString());
            String out = run("javap", "-c", "-p", dir.resolve("Calc.class").toString());

            // Print the method headers and their instructions, without CP indexes
            boolean inWanted = false;
            for (String line : out.lines().toList()) {
                String t = line.strip();
                if (t.endsWith(");")) {
                    inWanted = List.of("add(", "max(", "greet(", "square(").stream().anyMatch(t::contains);
                    if (inWanted) System.out.println(t);
                } else if (inWanted && t.matches("\\d+: .*")) {
                    System.out.println("    " + clean(t));
                }
            }
        } finally {
            try (var files = Files.list(dir)) {
                for (Path p : files.toList()) Files.delete(p);
            }
            Files.delete(dir);
        }
    }

    // "2: invokedynamic #7,  0   // InvokeDynamic #0:makeConcat..." -> "2: invokedynamic makeConcat..."
    static String clean(String instr) {
        String s = instr.replaceAll("#\\d+(,\\s+\\d+)?\\s*", "").replaceAll("\\s+//\\s+", " ");
        return s.replaceAll("InvokeDynamic \\S*?:", "").replaceAll("Method |Field ", "");
    }

    static String run(String tool, String... args) {
        ToolProvider tp = ToolProvider.findFirst(tool).orElseThrow();
        StringWriter out = new StringWriter();
        int rc = tp.run(new PrintWriter(out), new PrintWriter(out), args);
        if (rc != 0) throw new IllegalStateException(tool + " failed:\n" + out);
        return out.toString();
    }
}

/* Output:
int add(int, int);
    0: iload_1
    1: iload_2
    2: iadd
    3: ireturn
int max(int, int);
    0: iload_1
    1: iload_2
    2: if_icmple     9
    5: iload_1
    6: goto          10
    9: iload_2
    10: ireturn
java.lang.String greet(java.lang.String);
    0: aload_1
    1: invokedynamic makeConcatWithConstants:(Ljava/lang/String;)Ljava/lang/String;
    6: areturn
static long square(long);
    0: lload_0
    1: lload_0
    2: lmul
    3: lreturn
*/
