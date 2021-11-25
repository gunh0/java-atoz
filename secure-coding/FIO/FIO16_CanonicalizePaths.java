// FIO16-J. Canonicalize path names before validating them
// https://wiki.sei.cmu.edu/confluence/display/java/FIO16-J.+Canonicalize+path+names+before+validating+them
//
// A path can name the same file in many ways: "uploads/../secret.txt",
// "uploads/./a/../../secret.txt", or a symbolic link inside uploads that
// points elsewhere. Validating the raw string (startsWith, contains("..")) or
// even a normalized Path checks the spelling, not the file that will actually
// be opened. Resolve the name to its canonical form first (Path.toRealPath()
// resolves "." / ".." and follows symlinks, File.getCanonicalPath() is the
// older equivalent), then check that it is inside the allowed directory.
// This demo builds its own sandbox under a temporary directory.
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FIO16_CanonicalizePaths {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("fio16").toRealPath();
        Path uploads = Files.createDirectory(root.resolve("uploads"));
        Path secret = Files.writeString(root.resolve("secret.txt"), "TOP SECRET");
        Path report = Files.writeString(uploads.resolve("report.txt"), "quarterly report");
        Path subdir = Files.createDirectory(uploads.resolve("a"));
        Path link = uploads.resolve("innocent.txt");
        boolean symlinks = tryCreateSymlink(link, secret);
        try {
            String[] requests = {
                    "report.txt",
                    "../secret.txt",
                    "a/../../secret.txt",
                    symlinks ? "innocent.txt" : null,
            };
            for (String name : requests) {
                if (name == null) {
                    continue;
                }
                System.out.println("request [" + name + "]");
                System.out.println("  noncompliant (absolute):     " + readNoncompliant(uploads, name));
                System.out.println("  normalize() only:            " + readNormalized(uploads, name));
                System.out.println("  compliant (toRealPath):      " + readCompliant(uploads, name));
            }
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(report);
            Files.deleteIfExists(subdir);
            Files.deleteIfExists(secret);
            Files.deleteIfExists(uploads);
            Files.deleteIfExists(root);
        }
    }

    // Noncompliant: an absolute path still contains "..", so the check only
    // looks at the spelling, not at the file that will be opened
    static String readNoncompliant(Path uploads, String name) {
        Path candidate = uploads.resolve(name).toAbsolutePath();
        if (!candidate.startsWith(uploads)) {
            return "rejected";
        }
        return read(candidate);
    }

    // Better, but still noncompliant: normalize() removes ".." lexically and
    // never looks at the file system, so a symlink still escapes
    static String readNormalized(Path uploads, String name) {
        Path candidate = uploads.resolve(name).normalize();
        if (!candidate.startsWith(uploads)) {
            return "rejected";
        }
        return read(candidate);
    }

    // Compliant: canonicalize (resolve .., follow links), then validate
    static String readCompliant(Path uploads, String name) {
        try {
            Path base = uploads.toRealPath();
            Path canonical = base.resolve(name).toRealPath();
            if (!canonical.startsWith(base)) {
                return "rejected";
            }
            return read(canonical);
        } catch (IOException e) {
            return "rejected (" + e.getClass().getSimpleName() + ")";
        }
    }

    static String read(Path p) {
        try {
            return "read \"" + Files.readString(p) + "\"";
        } catch (IOException e) {
            return "error (" + e.getClass().getSimpleName() + ")";
        }
    }

    // Symbolic links may be unavailable (e.g. on Windows without privileges)
    static boolean tryCreateSymlink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (IOException | UnsupportedOperationException e) {
            System.out.println("(symbolic links not supported here; skipping that case)");
            return false;
        }
    }
}

/* Output:
request [report.txt]
  noncompliant (absolute):     read "quarterly report"
  normalize() only:            read "quarterly report"
  compliant (toRealPath):      read "quarterly report"
request [../secret.txt]
  noncompliant (absolute):     read "TOP SECRET"
  normalize() only:            rejected
  compliant (toRealPath):      rejected
request [a/../../secret.txt]
  noncompliant (absolute):     read "TOP SECRET"
  normalize() only:            rejected
  compliant (toRealPath):      rejected
request [innocent.txt]
  noncompliant (absolute):     read "TOP SECRET"
  normalize() only:            read "TOP SECRET"
  compliant (toRealPath):      rejected
*/
