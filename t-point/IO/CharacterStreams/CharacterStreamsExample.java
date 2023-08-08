// Character streams (Reader / Writer) move chars, not bytes. A charset is
// the mapping between the two, and it is where most text I/O bugs come from:
//  - InputStreamReader / OutputStreamWriter bridge bytes <-> chars with the
//    charset you pass; always pass one explicitly
//  - FileReader / FileWriter take a Charset since Java 11 (and default to
//    UTF-8 since Java 18), older code silently used the platform charset
//  - decoding with the wrong charset does not fail by default, it produces
//    mojibake or U+FFFD replacement characters
// StringReader / StringWriter are the in-memory versions.
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class CharacterStreamsExample {
    // Print a string with non-ASCII chars escaped, so stdout encoding does not matter
    static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            sb.append(c < 128 ? String.valueOf(c) : String.format("\\u%04X", (int) c));
        }
        return sb.toString();
    }

    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("chars");
        Path file = dir.resolve("users.txt");
        String text = "user=jos\u00e9\nuser=\u00fcmit\n"; // two accented letters, written as escapes
        try {
            try (Writer w = new FileWriter(file.toFile(), StandardCharsets.UTF_8)) {
                w.write(text);
            }
            System.out.println("chars written: " + text.length() + ", bytes on disk: " + Files.size(file));

            // Read back with the right charset
            StringWriter sw = new StringWriter();
            try (Reader r = new FileReader(file.toFile(), StandardCharsets.UTF_8)) {
                r.transferTo(sw);
            }
            System.out.println("UTF-8 read:      " + escape(sw.toString().lines().toList().toString()));
            System.out.println("round trip equal? " + sw.toString().equals(text));

            // Read back with the wrong charset: no exception, just wrong text
            StringWriter wrong = new StringWriter();
            try (Reader r = new InputStreamReader(Files.newInputStream(file), StandardCharsets.ISO_8859_1)) {
                r.transferTo(wrong);
            }
            System.out.println("ISO-8859-1 read: " + escape(wrong.toString().lines().toList().toString()));

            // US-ASCII cannot decode those bytes: they become U+FFFD
            String ascii = new String(Files.readAllBytes(file), StandardCharsets.US_ASCII);
            System.out.println("US-ASCII read:   " + escape(ascii.lines().toList().toString()));

            // Reading char by char from an in-memory Reader
            int letters = 0, c;
            try (Reader r = new StringReader("a1b2-c3")) {
                while ((c = r.read()) != -1) {
                    if (Character.isLetter(c)) letters++;
                }
            }
            System.out.println("letters in \"a1b2-c3\": " + letters);
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }
}

/* Output:
chars written: 20, bytes on disk: 22
UTF-8 read:      [user=jos\u00E9, user=\u00FCmit]
round trip equal? true
ISO-8859-1 read: [user=jos\u00C3\u00A9, user=\u00C3\u00BCmit]
US-ASCII read:   [user=jos\uFFFD\uFFFD, user=\uFFFD\uFFFDmit]
letters in "a1b2-c3": 3
*/
