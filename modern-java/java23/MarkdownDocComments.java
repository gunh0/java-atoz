// JEP 467 — Markdown Documentation Comments (Java 23)
//
// Javadoc comments have been written in HTML plus @-tags since 1995:
// <p> between paragraphs, <code>...</code> for code, <ul><li> for lists,
// &lt; for every "<". They are tedious to write and hard to read in the
// source, which is where most people read them. Since Java 23 a comment
// whose lines start with `///` is Markdown (CommonMark): blank lines make
// paragraphs, `backticks` make code, - makes lists, **bold**, fenced code
// blocks, and [links][java.util.List] to program elements. Block tags such
// as @param and @return still work. This program runs the javadoc tool
// in-process on a small source file and prints the HTML it generated.
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

public class MarkdownDocComments {
    static final String SOURCE = """
            package demo;

            public class Calc {
                /**
                 * Before: HTML in a traditional comment.
                 * <p>
                 * Returns <code>a + b</code>. Fails if the result
                 * <em>overflows</em> an <code>int</code>.
                 */
                public static int addHtml(int a, int b) {
                    return Math.addExact(a, b);
                }

                /// After: Markdown in a `///` comment.
                ///
                /// Returns `a + b`. Fails if the result
                /// _overflows_ an `int`:
                ///
                /// - uses [Math#addExact(int, int)]
                /// - throws **ArithmeticException**
                ///
                /// @param a the first addend
                /// @param b the second addend
                /// @return the exact sum
                public static int addMarkdown(int a, int b) {
                    return Math.addExact(a, b);
                }
            }
            """;

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("jep467");
        try {
            Path src = dir.resolve("src/demo/Calc.java");
            Files.createDirectories(src.getParent());
            Files.writeString(src, SOURCE);
            Path out = dir.resolve("api");

            ToolProvider javadoc = ToolProvider.findFirst("javadoc").orElseThrow();
            StringWriter log = new StringWriter(); // javadoc's progress messages, not printed
            int rc = javadoc.run(new PrintWriter(log), new PrintWriter(log),
                    "-quiet", "-d", out.toString(), "-sourcepath", dir.resolve("src").toString(), "demo");
            System.out.println("javadoc exit code: " + rc);

            String html = Files.readString(out.resolve("demo/Calc.html"));
            for (String method : new String[]{"addHtml", "addMarkdown"}) {
                System.out.println("--- " + method + " description:");
                System.out.println(description(html, method));
            }
        } finally {
            try (Stream<Path> files = Files.walk(dir)) {
                files.sorted(Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
            }
        }
    }

    // The <div class="block"> in the method's detail section holds the rendered comment
    static String description(String html, String method) {
        int detail = html.indexOf("<section class=\"detail\" id=\"" + method + "(int,int)\">");
        Matcher m = Pattern.compile("<div class=\"block\">(.*?)</div>", Pattern.DOTALL).matcher(html);
        if (detail < 0 || !m.find(detail)) {
            return "(not found)";
        }
        return m.group(1).strip().replaceAll("<a href=\"[^\"]*\"[^>]*>", "<a>"); // drop JDK doc URLs
    }
}

/* Output:
javadoc exit code: 0
--- addHtml description:
Before: HTML in a traditional comment.
 <p>
 Returns <code>a + b</code>. Fails if the result
 <em>overflows</em> an <code>int</code>.
--- addMarkdown description:
<p>After: Markdown in a <code>///</code> comment.</p>
<p>Returns <code>a + b</code>. Fails if the result
<em>overflows</em> an <code>int</code>:</p>
<ul>
<li>uses <a><code>Math.addExact(int, int)</code></a></li>
<li>throws <strong>ArithmeticException</strong></li>
</ul>
*/
