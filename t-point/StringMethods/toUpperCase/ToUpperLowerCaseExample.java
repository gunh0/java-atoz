// toUpperCase() / toLowerCase() return a NEW string with every character's
// case converted (strings are immutable, the original is untouched).
//  - The no-argument versions use the JVM's DEFAULT LOCALE, so the result can
//    change from one machine to another. The classic trap is Turkish, where
//    "i".toUpperCase() is a dotted capital I (U+0130), not "I".
//  - For identifiers, protocol keywords, file extensions and other
//    machine-readable text, always pass Locale.ROOT.
//  - Case conversion can change the length: German sharp s becomes "SS".
//  - To compare ignoring case, prefer equalsIgnoreCase() over converting both.
import java.util.Locale;

public class ToUpperLowerCaseExample {
    public static void main(String[] args) {
        String name = "Hello, World";
        System.out.println(name.toUpperCase(Locale.ROOT));
        System.out.println(name.toLowerCase(Locale.ROOT));
        System.out.println("original unchanged: " + name);

        // Locale-sensitive mapping: the same input, two different answers
        Locale turkish = Locale.forLanguageTag("tr");
        System.out.println("\"title\" ROOT    -> " + escape("title".toUpperCase(Locale.ROOT)));
        System.out.println("\"title\" Turkish -> " + escape("title".toUpperCase(turkish)));
        System.out.println("\"TITLE\" Turkish -> " + escape("TITLE".toLowerCase(turkish)));

        // Why it matters: a keyword check that silently fails on a Turkish JVM
        String header = "content-encoding: gzip";
        String key = header.substring(0, header.indexOf(':'));
        System.out.println("ROOT match:    " + key.toUpperCase(Locale.ROOT).equals("CONTENT-ENCODING"));
        System.out.println("Turkish match: " + key.toUpperCase(turkish).equals("CONTENT-ENCODING"));

        // Length can change: one char in, two chars out
        String street = "Stra\u00DFe"; // "Strasse" written with sharp s
        String upper = street.toUpperCase(Locale.ROOT);
        System.out.println(escape(street) + " (" + street.length() + ") -> "
                + upper + " (" + upper.length() + ")");

        // Comparing ignoring case: no temporary strings, no locale surprises
        System.out.println("\"GZIP\".equalsIgnoreCase(\"gzip\") = " + "GZIP".equalsIgnoreCase("gzip"));

        // Converting a string that is already in the target case returns the same object
        String lower = "already lower";
        System.out.println("same instance when nothing changes: "
                + (lower == lower.toLowerCase(Locale.ROOT)));
    }

    // Show non-ASCII characters as \\uXXXX so the output is plain ASCII
    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            sb.append(c < 128 ? String.valueOf(c) : String.format("\\u%04X", (int) c));
        }
        return sb.toString();
    }
}

/* Output:
HELLO, WORLD
hello, world
original unchanged: Hello, World
"title" ROOT    -> TITLE
"title" Turkish -> T\u0130TLE
"TITLE" Turkish -> t\u0131tle
ROOT match:    true
Turkish match: false
Stra\u00DFe (6) -> STRASSE (7)
"GZIP".equalsIgnoreCase("gzip") = true
same instance when nothing changes: true
*/
