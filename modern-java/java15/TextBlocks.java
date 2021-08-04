// JEP 378 — Text Blocks (Java 15)
//
// Embedding JSON, SQL or HTML in a string literal used to require \n, escaped
// quotes and + on every line. A text block starts with """ followed by a line
// break and ends with """. The compiler removes the incidental indentation
// (the common leading whitespace, relative to the closing delimiter), strips
// trailing spaces, and normalizes line endings to \n. Two new escapes exist:
// \<newline> joins lines (no line break), and \s keeps a single space that
// would otherwise be stripped. String::formatted fills in placeholders.
public class TextBlocks {
    public static void main(String[] args) {
        // Before: escapes and concatenation
        String jsonOld = "{\n" +
                "  \"name\": \"Duke\",\n" +
                "  \"age\": 26\n" +
                "}\n";

        // After: the text looks like the text
        String json = """
                {
                  "name": "Duke",
                  "age": 26
                }
                """;
        System.out.println("same string: " + json.equals(jsonOld));
        System.out.print(json);

        // The closing delimiter's position decides the indentation that stays
        String indented = """
                    SELECT id, name
                    FROM users
                    WHERE active = true
                """;
        System.out.print(indented);

        // Closing delimiter on the last text line: no trailing newline
        String noNewline = """
                one line""";
        System.out.println("[" + noNewline + "]");

        // \<newline>: break a long line in the source only
        String sentence = """
                This is a single, long line \
                written over two source lines.""";
        System.out.println(sentence);

        // \s: keep trailing spaces (here, fixed-width columns)
        String table = """
                red  \s
                green\s
                """;
        table.lines().forEach(line -> System.out.println("[" + line + "]"));

        // Placeholders
        String html = """
                <p>Hello, %s! You have %d new messages.</p>
                """.formatted("Duke", 3);
        System.out.print(html);
    }
}

/* Output:
same string: true
{
  "name": "Duke",
  "age": 26
}
    SELECT id, name
    FROM users
    WHERE active = true
[one line]
This is a single, long line written over two source lines.
[red   ]
[green ]
<p>Hello, Duke! You have 3 new messages.</p>
*/
