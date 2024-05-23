// Pattern flags change how a regex is interpreted. Pass them to
// Pattern.compile(regex, flags) or embed them in the regex itself:
//   CASE_INSENSITIVE (?i)  ASCII-only case folding ...
//   UNICODE_CASE     (?u)  ... unless combined with this flag
//   MULTILINE        (?m)  ^ and $ match at every line, not just the input ends
//   DOTALL           (?s)  . also matches line terminators
//   COMMENTS         (?x)  whitespace is ignored and # starts a comment
//   LITERAL                the whole pattern is plain text (like Pattern.quote)
// Embedded flags can be scoped to part of the pattern, e.g. (?i:abc)def,
// and turned off with a minus sign, e.g. (?-i).
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FlagsExample {
    static List<String> all(Pattern p, String input) {
        List<String> found = new ArrayList<>();
        Matcher m = p.matcher(input);
        while (m.find()) {
            found.add(m.group().replace("\n", "\\n"));
        }
        return found;
    }

    public static void main(String[] args) {
        // CASE_INSENSITIVE is ASCII-only by default
        String word = "\u00C9COLE"; // capital E with acute accent
        System.out.println("CASE_INSENSITIVE:              "
                + Pattern.compile("\u00E9cole", Pattern.CASE_INSENSITIVE).matcher(word).matches());
        System.out.println("CASE_INSENSITIVE|UNICODE_CASE: "
                + Pattern.compile("\u00E9cole", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(word).matches());
        System.out.println("embedded (?iu):                " + word.matches("(?iu)\u00E9cole"));

        // MULTILINE: ^ and $ per line
        String config = "host=a\nport=80\n# comment\nuser=root";
        System.out.println("without MULTILINE: " + all(Pattern.compile("^\\w+(?==)"), config));
        System.out.println("with MULTILINE:    " + all(Pattern.compile("^\\w+(?==)", Pattern.MULTILINE), config));

        // DOTALL: . crosses line breaks
        String html = "<p>first\nsecond</p>";
        System.out.println("without DOTALL: " + all(Pattern.compile("<p>.*</p>"), html));
        System.out.println("with DOTALL:    " + all(Pattern.compile("<p>.*</p>", Pattern.DOTALL), html));

        // COMMENTS: a readable, annotated pattern (escape real spaces as \\s or [ ])
        Pattern ipv4 = Pattern.compile("""
                ^
                (\\d{1,3})      # first octet
                (?:\\.\\d{1,3}){3}  # three more octets
                $
                """, Pattern.COMMENTS);
        System.out.println("COMMENTS ipv4 192.168.0.1: " + ipv4.matcher("192.168.0.1").matches());

        // LITERAL: metacharacters lose their meaning
        System.out.println("a.b as regex on axb:   " + Pattern.compile("a.b").matcher("axb").matches());
        System.out.println("a.b as LITERAL on axb: " + Pattern.compile("a.b", Pattern.LITERAL).matcher("axb").matches());

        // Scoped embedded flags: only "error" is case-insensitive
        Pattern scoped = Pattern.compile("(?i:error):(?-i)[A-Z]+");
        for (String s : List.of("ERROR:DISK", "error:DISK", "Error:disk")) {
            System.out.println("(?i:error):(?-i)[A-Z]+ on " + s + " -> " + scoped.matcher(s).matches());
        }
    }
}

/* Output:
CASE_INSENSITIVE:              false
CASE_INSENSITIVE|UNICODE_CASE: true
embedded (?iu):                true
without MULTILINE: [host]
with MULTILINE:    [host, port, user]
without DOTALL: []
with DOTALL:    [<p>first\nsecond</p>]
COMMENTS ipv4 192.168.0.1: true
a.b as regex on axb:   true
a.b as LITERAL on axb: false
(?i:error):(?-i)[A-Z]+ on ERROR:DISK -> true
(?i:error):(?-i)[A-Z]+ on error:DISK -> true
(?i:error):(?-i)[A-Z]+ on Error:disk -> false
*/
