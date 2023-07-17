// Effective Java, Item 56: Write doc comments for all exposed API elements.
//
// Javadoc is how an API is documented. Every exported class, interface,
// constructor, method and field needs a doc comment. A method's comment
// states its contract: what it does (not how), preconditions (@throws for
// unchecked exceptions, @param), postconditions and side effects, and thread
// safety. The first sentence is the summary and should be unique per member.
// Use {@code} for code, {@literal} for <, > and &, @implSpec for what
// subclasses may rely on, and document every type parameter, enum constant
// and annotation member. javac's doclint (-Xdoclint) checks much of this,
// which this program runs in-process on two versions of the same class.
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

public class DocComments {
    static final String UNDOCUMENTED = """
            public class Stack<E> {
                public void push(E e) { }
                public E pop() { return null; }
            }
            """;

    static final String DOCUMENTED = """
            /**
             * A last-in-first-out stack of elements.
             *
             * <p>Instances are not thread-safe.
             *
             * @param <E> the type of elements held in this stack
             */
            public class Stack<E> {
                /** Creates an empty stack. */
                public Stack() { }

                /**
                 * Pushes an element onto the top of this stack.
                 *
                 * @param e the element to push; may not be {@code null}
                 * @throws NullPointerException if {@code e} is {@code null}
                 */
                public void push(E e) {
                    if (e == null) throw new NullPointerException();
                }

                /**
                 * Removes and returns the element on top of this stack.
                 *
                 * @return the element that was on top of the stack
                 * @throws java.util.EmptyStackException if the stack is empty
                 */
                public E pop() {
                    throw new java.util.EmptyStackException();
                }
            }
            """;

    public static void main(String[] args) throws Exception {
        System.out.println("doclint on the undocumented class:");
        lint(UNDOCUMENTED);
        System.out.println("doclint on the documented class:");
        lint(DOCUMENTED);
    }

    /**
     * Compiles {@code source} as {@code Stack.java} with {@code -Xdoclint:all}
     * and prints each diagnostic as {@code line N: message}.
     *
     * @param source the Java source text of a public class named {@code Stack}
     * @throws java.io.IOException if the temporary output directory cannot be
     *                             created or deleted
     */
    static void lint(String source) throws java.io.IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///Stack.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        Path out = Files.createTempDirectory("doclint");
        try {
            javac.getTask(null, null, diagnostics, List.of("-Xdoclint:all", "-d", out.toString()), null, List.of(file))
                    .call();
            if (diagnostics.getDiagnostics().isEmpty()) {
                System.out.println("  no warnings");
            }
            diagnostics.getDiagnostics().forEach(d -> System.out.printf("  line %d: %s%n",
                    d.getLineNumber(), d.getMessage(Locale.ROOT).lines().findFirst().orElse("")));
        } finally {
            try (var files = Files.walk(out)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }
}

/* Output:
doclint on the undocumented class:
  line 1: no comment
  line 1: use of default constructor, which does not provide a comment
  line 2: no comment
  line 3: no comment
doclint on the documented class:
  no warnings
*/
