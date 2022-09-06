// Java 12 — String.indent and String.transform (API additions, no JEP)
//
// indent(n) adjusts the indentation of every line of a string: n > 0 adds n
// spaces, n < 0 removes up to n leading whitespace characters, and n == 0
// only normalizes line terminators. Each line ends with \n afterwards,
// including the last one. transform(f) applies any function to the string
// and returns its result, so custom steps can sit in a fluent chain instead
// of wrapping the whole expression inside out: f3(f2(f1(s))) becomes
// s.transform(f1).transform(f2).transform(f3).
import java.util.function.Function;

public class StringIndentTransform {
    public static void main(String[] args) {
        String code = "if (ready) {\n    start();\n}";

        // Show line boundaries with | and spaces as . so the whitespace is visible
        System.out.println("indent(4):");
        System.out.print(visible(code.indent(4)));
        System.out.println("indent(-2) removes at most 2 leading blanks per line:");
        System.out.print(visible("      deep\n  shallow\nnone".indent(-2)));
        System.out.println("indent(0) normalizes \\r\\n and adds the final \\n: "
                + "a\r\nb".indent(0).equals("a\nb\n"));

        // Before: manual loop over lines
        StringBuilder before = new StringBuilder();
        for (String line : code.split("\n")) {
            before.append("  ").append(line).append('\n');
        }
        System.out.println("matches the manual loop: " + before.toString().equals(code.indent(2)));

        // transform: inside-out calls become a left-to-right chain
        Function<String, String> mask = s -> s.replaceAll("\\d(?=\\d{4})", "*");
        Function<String, String> quote = s -> "\"" + s + "\"";
        String insideOut = quote.apply(mask.apply("4111222233334444".strip()));
        String chained = " 4111222233334444 ".strip().transform(mask).transform(quote);
        System.out.println("inside out: " + insideOut);
        System.out.println("transform:  " + chained);

        // The function may return any type
        int words = "the quick brown fox".transform(s -> s.split(" ").length);
        System.out.println("word count via transform: " + words);
    }

    static String visible(String s) {
        return s.replace(" ", ".").lines().map(l -> "  |" + l + "|\n").reduce("", String::concat);
    }
}


/* Output:
indent(4):
  |....if.(ready).{|
  |........start();|
  |....}|
indent(-2) removes at most 2 leading blanks per line:
  |....deep|
  |shallow|
  |none|
indent(0) normalizes \r\n and adds the final \n: true
matches the manual loop: true
inside out: "************4444"
transform:  "************4444"
word count via transform: 4
*/
