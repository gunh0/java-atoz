// Effective Java, Item 40: Consistently use the Override annotation.
//
// Writing equals(Bigram b) instead of equals(Object o) does not override
// Object.equals, it overloads it. The program compiles, HashSet keeps calling
// the identity-based Object.equals, and "equal" values are stored twice.
// Putting @Override on every method that is meant to override a superclass
// method turns that silent bug into a compile error. (Concrete methods that
// implement an abstract method are checked anyway, but the annotation costs
// nothing there either.) The second half of this demo hands both versions to
// the in-process compiler and prints what it says.
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

public class OverrideAnnotation {
    public static void main(String[] args) throws Exception {
        Set<Bigram> overloaded = new HashSet<>();
        Set<FixedBigram> overridden = new HashSet<>();
        for (int round = 0; round < 10; round++) {
            for (char ch = 'a'; ch <= 'z'; ch++) {
                overloaded.add(new Bigram(ch, ch));
                overridden.add(new FixedBigram(ch, ch));
            }
        }
        System.out.println("equals(Bigram) overload, set size: " + overloaded.size());
        System.out.println("equals(Object) override, set size: " + overridden.size());

        // What happens if the buggy version is marked @Override?
        String withOverride = """
                class Bigram {
                    final char first, second;
                    Bigram(char f, char s) { first = f; second = s; }
                    @Override public boolean equals(Bigram b) {
                        return b.first == first && b.second == second;
                    }
                    @Override public int hashCode() { return 31 * first + second; }
                }
                """;
        System.out.println("compiling the buggy class with @Override:");
        compile(withOverride);
    }

    static void compile(String source) throws Exception {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///Bigram.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        Path out = Files.createTempDirectory("override");
        try {
            boolean ok = javac.getTask(null, null, diagnostics, List.of("-d", out.toString()), null, List.of(file)).call();
            diagnostics.getDiagnostics().forEach(d -> System.out.printf("  line %d: %s: %s%n",
                    d.getLineNumber(), d.getKind(), d.getMessage(Locale.ROOT).lines().findFirst().orElse("")));
            System.out.println("  compiled: " + ok);
        } finally {
            try (var files = Files.walk(out)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }
}

// Bug: equals takes a Bigram, so it overloads instead of overriding.
class Bigram {
    private final char first, second;

    Bigram(char first, char second) {
        this.first = first;
        this.second = second;
    }

    public boolean equals(Bigram b) {
        return b.first == first && b.second == second;
    }

    @Override
    public int hashCode() {
        return 31 * first + second;
    }
}

class FixedBigram {
    private final char first, second;

    FixedBigram(char first, char second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FixedBigram b && b.first == first && b.second == second;
    }

    @Override
    public int hashCode() {
        return 31 * first + second;
    }
}

/* Output:
equals(Bigram) overload, set size: 260
equals(Object) override, set size: 26
compiling the buggy class with @Override:
  line 4: ERROR: method does not override or implement a method from a supertype
  compiled: false
*/
