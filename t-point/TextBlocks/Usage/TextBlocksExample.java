// A text block is a multi-line string literal between """ delimiters. The
// opening """ must end its line; the content starts on the next line.
// The compiler then:
//  - removes incidental indentation: the smallest indentation among the
//    content lines and the closing delimiter is stripped from every line,
//    so the position of the closing """ controls the left margin
//  - strips trailing spaces on each line and normalizes line endings to \n
//  - finally interprets escapes, including two that only exist for text
//    blocks: \s (a space that survives stripping) and \<newline> (join lines)
// String.stripIndent() and translateEscapes() expose the same steps at runtime.
public class TextBlocksExample {
    public static void main(String[] args) {
        // Closing delimiter on its own line, aligned with the content: no margin,
        // and the string ends with a newline.
        String sql = """
                SELECT id, name
                FROM users
                WHERE active = true
                """;
        show("sql", sql);

        // Closing delimiter further left: the content keeps 4 spaces of indent
        String indented = """
                    line one
                    line two
                """;
        show("indented", indented);

        // Closing delimiter right after the last line: no trailing newline
        String noNewline = """
                no trailing newline""";
        show("noNewline", noNewline);

        // \s keeps trailing spaces (fixed-width records), \<newline> joins lines
        String fixed = """
                abc  \s
                de   \s
                """;
        show("fixed", fixed);
        String joined = """
                a very long sentence that we \
                split in the source only""";
        show("joined", joined);

        // Quotes need no escaping, except three in a row
        String json = """
                {"name": "alice", "note": "says \""" here"}""";
        show("json", json);

        // Templating with formatted()
        String html = """
                <ul>
                  <li>%s</li>
                  <li>%s</li>
                </ul>
                """.formatted("first", "second");
        show("html", html);

        // A text block is just a String: same value as the escaped literal
        String classic = "SELECT id, name\nFROM users\nWHERE active = true\n";
        System.out.println("sql equals classic literal: " + sql.equals(classic));

        // The runtime equivalents
        String raw = "    x\n      y\n    z";
        show("stripIndent", raw.stripIndent());
        show("translateEscapes", "tab:\\t|newline:\\n|".translateEscapes());
    }

    // Make line ends and spaces visible: '\n' -> "\\n", space -> '~'
    static void show(String label, String s) {
        String visible = s.replace(" ", "~").replace("\t", "\\t").replace("\n", "\\n\n");
        System.out.println("--- " + label + " (" + s.length() + " chars)");
        System.out.println(visible.endsWith("\n") ? visible.substring(0, visible.length() - 1) : visible);
    }
}

/* Output:
--- sql (47 chars)
SELECT~id,~name\n
FROM~users\n
WHERE~active~=~true\n
--- indented (26 chars)
~~~~line~one\n
~~~~line~two\n
--- noNewline (19 chars)
no~trailing~newline
--- fixed (14 chars)
abc~~~\n
de~~~~\n
--- joined (53 chars)
a~very~long~sentence~that~we~split~in~the~source~only
--- json (42 chars)
{"name":~"alice",~"note":~"says~"""~here"}
--- html (46 chars)
<ul>\n
~~<li>first</li>\n
~~<li>second</li>\n
</ul>\n
sql equals classic literal: true
--- stripIndent (7 chars)
x\n
~~y\n
z
--- translateEscapes (16 chars)
tab:\t|newline:\n
|
*/
