// FIO03-J. Remove temporary files before termination
// https://wiki.sei.cmu.edu/confluence/display/java/FIO03-J.+Remove+temporary+files+before+termination
//
// Temporary files often hold sensitive intermediate data (decrypted
// content, exports, uploads). File.deleteOnExit() only removes them when
// the JVM exits normally, so a long-running server keeps them until a
// restart and a crash or kill leaves them behind for good (and on Windows a
// file that is still open cannot be deleted at all). Delete temporary
// files as soon as they are no longer needed: open them with
// StandardOpenOption.DELETE_ON_CLOSE, or delete them in a finally block /
// try-with-resources. (Create them with Files.createTempFile in a private
// directory, too.)
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FIO03_TempFileRemoval {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("fio03");
        try {
            exportNoncompliant(dir);
            System.out.println("noncompliant: files left after the export: " + count(dir));

            clean(dir); // undo the noncompliant leftovers for the next step
            exportCompliant(dir);
            System.out.println("compliant:    files left after the export: " + count(dir));
        } finally {
            clean(dir);
            Files.delete(dir);
        }
    }

    // Noncompliant: the decrypted data stays on disk until the JVM exits (if it ever does normally)
    static void exportNoncompliant(Path dir) throws IOException {
        File tmp = File.createTempFile("decrypted", ".csv", dir.toFile());
        tmp.deleteOnExit();
        Files.writeString(tmp.toPath(), CSV);
        upload(Files.readAllBytes(tmp.toPath()));
    }

    // Compliant: the file is removed when the channel is closed (on Linux and
    // macOS the name disappears right away; the data stays reachable through
    // the open channel only)
    static void exportCompliant(Path dir) throws IOException {
        Path tmp = Files.createTempFile(dir, "decrypted", ".csv");
        try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.READ, StandardOpenOption.WRITE,
                StandardOpenOption.DELETE_ON_CLOSE)) {
            ch.write(ByteBuffer.wrap(CSV.getBytes(StandardCharsets.UTF_8)));
            ByteBuffer back = ByteBuffer.allocate((int) ch.size());
            ch.read(back, 0);
            upload(back.array());
        } finally {
            Files.deleteIfExists(tmp); // belt and braces, e.g. where DELETE_ON_CLOSE is unsupported
        }
    }

    static final String CSV = "name,ssn\nalice,123-45-6789\n";

    static void upload(byte[] data) {
        System.out.println("  uploaded " + data.length + " bytes");
    }

    static long count(Path dir) throws IOException {
        try (var files = Files.list(dir)) {
            return files.count();
        }
    }

    static void clean(Path dir) throws IOException {
        try (var files = Files.list(dir)) {
            for (Path p : files.toList()) {
                Files.delete(p);
            }
        }
    }
}

/* Output:
  uploaded 27 bytes
noncompliant: files left after the export: 1
  uploaded 27 bytes
compliant:    files left after the export: 0
*/
