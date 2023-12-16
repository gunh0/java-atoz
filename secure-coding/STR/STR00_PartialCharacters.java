// STR00-J. Don't form strings containing partial characters from variable-width encodings
// https://wiki.sei.cmu.edu/confluence/display/java/STR00-J.+Don%27t+form+strings+containing+partial+characters+from+variable-width+encodings
//
// In UTF-8 a character takes one to four bytes. Code that reads a stream in
// fixed-size blocks and decodes each block on its own with
// new String(block, UTF_8) cuts multi-byte characters at block boundaries;
// each half is malformed and is replaced with U+FFFD, so the text is
// silently corrupted (names, keys and signatures computed over it no
// longer match). Decode the stream as a whole: wrap it in an
// InputStreamReader (its CharsetDecoder carries partial sequences over to
// the next read), or collect all bytes first and decode once.
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public class STR00_PartialCharacters {
    static final int BLOCK = 4;

    public static void main(String[] args) throws IOException {
        String name = "José Müller 김민준";
        byte[] utf8 = name.getBytes(StandardCharsets.UTF_8);
        String bad = readNoncompliant(new ByteArrayInputStream(utf8));
        String good = readCompliant(new ByteArrayInputStream(utf8));

        System.out.println("noncompliant intact? " + bad.equals(name) + ", replacement chars: " + bad.chars().filter(c -> c == 0xFFFD).count());
        System.out.println("compliant intact?    " + good.equals(name));
    }

    // Noncompliant: decodes each 4-byte block separately
    static String readNoncompliant(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        byte[] block = new byte[BLOCK];
        int n;
        while ((n = in.read(block)) != -1) {
            sb.append(new String(block, 0, n, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    // Compliant: a Reader decodes across block boundaries
    static String readCompliant(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] block = new char[BLOCK];
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            int n;
            while ((n = reader.read(block)) != -1) {
                sb.append(block, 0, n);
            }
        }
        return sb.toString();
    }
}

/* Output:
noncompliant intact? false, replacement chars: 6
compliant intact?    true
*/
