// Java 12 — Files.mismatch (API addition, no JEP)
//
// Comparing two files used to mean reading both into byte arrays and calling
// Arrays.equals (wasteful for large files), or writing a buffered loop that
// tracks the position. Files.mismatch(a, b) compares the contents and returns
// -1 if they are identical, otherwise the position of the first differing
// byte; if one file is a prefix of the other, it returns the length of the
// shorter one. It is handy for checking a download or a generated file
// against a known-good copy.
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class FilesMismatch {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("mismatch");
        try {
            Path original = Files.writeString(dir.resolve("original.txt"), "version=1.4.2\nmode=release\n");
            Path copy = Files.writeString(dir.resolve("copy.txt"), "version=1.4.2\nmode=release\n");
            Path patched = Files.writeString(dir.resolve("patched.txt"), "version=1.4.3\nmode=release\n");
            Path truncated = Files.writeString(dir.resolve("truncated.txt"), "version=1.4.2\n");

            // Before: read everything, learn only equal / not equal
            boolean sameBefore = Arrays.equals(Files.readAllBytes(original), Files.readAllBytes(patched));
            System.out.println("before: Arrays.equals = " + sameBefore);

            // After: -1 or the first differing position
            System.out.println("identical copy:  " + Files.mismatch(original, copy));
            long pos = Files.mismatch(original, patched);
            System.out.println("patched file:    " + pos + " (byte '" + (char) Files.readAllBytes(original)[(int) pos]
                    + "' vs '" + (char) Files.readAllBytes(patched)[(int) pos] + "')");
            System.out.println("prefix file:     " + Files.mismatch(original, truncated)
                    + " (= size of the shorter file, " + Files.size(truncated) + ")");
            System.out.println("same path:       " + Files.mismatch(original, original));
        } finally {
            try (var files = Files.list(dir)) {
                for (Path p : files.toList()) {
                    Files.delete(p);
                }
            }
            Files.delete(dir);
        }
    }
}

/* Output:
before: Arrays.equals = false
identical copy:  -1
patched file:    12 (byte '2' vs '3')
prefix file:     14 (= size of the shorter file, 14)
same path:       -1
*/
