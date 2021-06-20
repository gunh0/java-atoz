// startsWith(prefix) and endsWith(suffix) test the beginning and the end of a
// string. startsWith(prefix, offset) checks for the prefix at a given index,
// which is handy when walking through a string. Both are case-sensitive and
// compare plain text; the empty string is a prefix and suffix of every string.
import java.util.List;
import java.util.Locale;

public class StartsWithEndsWithExample {
    public static void main(String[] args) {
        String url = "https://example.com/docs/index.html";

        System.out.println(url.startsWith("https://"));    // true
        System.out.println(url.startsWith("HTTPS://"));    // false: case-sensitive
        System.out.println(url.endsWith(".html"));         // true
        System.out.println(url.startsWith(""));            // true
        System.out.println(url.endsWith(""));              // true

        // startsWith(prefix, offset): does "example" begin at index 8?
        System.out.println(url.startsWith("example", 8));  // true
        System.out.println(url.startsWith("example", 0));  // false
        System.out.println(url.startsWith("x", -1));       // false (no exception)

        // Typical use: filter by extension (normalize case first)
        List<String> files = List.of("Report.PDF", "notes.txt", "scan.pdf", "pdf.doc");
        for (String file : files) {
            if (file.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                System.out.println("PDF: " + file);
            }
        }

        // Typical use: strip a known prefix only when it is present
        String header = "Bearer abc.def.ghi";
        String prefix = "Bearer ";
        if (header.startsWith(prefix)) {
            System.out.println("token = " + header.substring(prefix.length()));
        }
    }
}

/* Output:
true
false
true
true
true
true
false
false
PDF: Report.PDF
PDF: scan.pdf
token = abc.def.ghi
*/
