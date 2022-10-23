// JEP 400 — UTF-8 by Default (Java 18)
//
// Many Java APIs that read or write text take an optional charset and fall
// back to "the default charset" when none is given: FileReader, FileWriter,
// InputStreamReader, PrintStream, String.getBytes(), new String(bytes),
// Scanner, Formatter... Before Java 18 that default came from the operating
// system's locale, so the same program wrote UTF-8 on Linux and macOS but
// windows-1252 (or MS949 on Korean Windows) on Windows, and files written on
// one machine were garbled on another. Since Java 18 the default charset is
// UTF-8 everywhere. The old platform value is still available as the
// native.encoding property, and -Dfile.encoding=COMPAT restores the old
// behaviour for applications that depended on it.
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

public class Utf8ByDefault {
    public static void main(String[] args) throws IOException {
        System.out.println("Charset.defaultCharset(): " + Charset.defaultCharset());
        System.out.println("file.encoding:            " + System.getProperty("file.encoding"));
        System.out.println("native.encoding is set:   " + (System.getProperty("native.encoding") != null));

        // Text with characters outside ASCII ("cafe" with an e-acute, the Korean "Hangul")
        String text = "café 한글";

        Path dir = Files.createTempDirectory("jep400");
        Path file = dir.resolve("note.txt");
        try {
            // No charset given anywhere: the default is used
            try (Writer w = new FileWriter(file.toFile())) {
                w.write(text);
            }
            byte[] raw = Files.readAllBytes(file);
            System.out.println("bytes on disk:            " + HexFormat.ofDelimiter(" ").formatHex(raw));
            System.out.println("same as explicit UTF-8:   "
                    + java.util.Arrays.equals(raw, text.getBytes(StandardCharsets.UTF_8)));

            char[] buf = new char[32];
            int n;
            try (Reader r = new FileReader(file.toFile())) {
                n = r.read(buf);
            }
            System.out.println("read back intact:         " + new String(buf, 0, n).equals(text));

            // String.getBytes() without a charset is now portable too
            System.out.println("getBytes() length:        " + text.getBytes().length + " bytes for "
                    + text.length() + " chars");

            // What a single-byte platform default (e.g. Latin-1) used to do to it
            byte[] latin1 = text.getBytes(StandardCharsets.ISO_8859_1);
            System.out.println("Latin-1 round trip intact: "
                    + new String(latin1, StandardCharsets.ISO_8859_1).equals(text)
                    + " (Hangul became " + new String(latin1, StandardCharsets.ISO_8859_1).substring(5) + ")");
        } finally {
            Files.deleteIfExists(file);
            Files.delete(dir);
        }
    }
}

/* Output:
Charset.defaultCharset(): UTF-8
file.encoding:            UTF-8
native.encoding is set:   true
bytes on disk:            63 61 66 c3 a9 20 ed 95 9c ea b8 80
same as explicit UTF-8:   true
read back intact:         true
getBytes() length:        12 bytes for 7 chars
Latin-1 round trip intact: false (Hangul became ??)
*/
