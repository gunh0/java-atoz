// Java 11 — Files.readString and Files.writeString (API additions, no JEP)
//
// Reading a whole text file used to mean new String(Files.readAllBytes(p), UTF_8)
// or a BufferedReader loop, and remembering to name the charset every time.
// Files.readString / Files.writeString do it in one call and default to UTF-8
// (not the platform charset). readString is strict: malformed input throws
// MalformedInputException instead of silently inserting replacement chars.
// Both load the whole file into memory, so they are meant for small files.
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FilesReadWriteString {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("java11-files");
        Path file = dir.resolve("notes.txt");
        Path broken = dir.resolve("broken.txt");
        try {
            // Before Java 11
            Files.write(file, "caf\u00e9\n".getBytes(StandardCharsets.UTF_8));
            StringBuilder before = new StringBuilder();
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    before.append(line).append('\n');
                }
            }
            System.out.println("before: read back \"caf\\u00e9\": " + before.toString().equals("caf\u00e9\n"));

            // After: one call each way, UTF-8 by default
            Files.writeString(file, "line one\n");
            Files.writeString(file, "line two\n", StandardOpenOption.APPEND);
            String content = Files.readString(file);
            System.out.print(content);
            System.out.println("lines: " + content.lines().count() + ", bytes: " + Files.size(file));

            // Non-ASCII text round-trips without naming a charset
            Files.writeString(file, "caf\u00e9");
            System.out.println("bytes for 4 chars: " + Files.size(file)
                    + ", round-trip ok: " + Files.readString(file).equals("caf\u00e9"));

            // Invalid UTF-8 is reported, not papered over
            Files.write(broken, new byte[] {'o', 'k', (byte) 0xFF});
            try {
                Files.readString(broken);
            } catch (MalformedInputException e) {
                System.out.println("readString on invalid UTF-8: " + e.getMessage());
            }
            // The old String constructor silently substitutes U+FFFD instead
            String lenient = new String(Files.readAllBytes(broken), StandardCharsets.UTF_8);
            System.out.println("new String(...) length " + lenient.length()
                    + ", last char is U+FFFD: " + (lenient.charAt(2) == '\ufffd'));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(broken);
            Files.deleteIfExists(dir);
        }
    }
}

/* Output:
before: read back "caf\u00e9": true
line one
line two
lines: 2, bytes: 18
bytes for 4 chars: 5, round-trip ok: true
readString on invalid UTF-8: Input length = 1
new String(...) length 3, last char is U+FFFD: true
*/
