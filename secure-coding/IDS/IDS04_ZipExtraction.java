// IDS04-J. Safely extract files from ZipInputStream
// https://wiki.sei.cmu.edu/confluence/display/java/IDS04-J.+Safely+extract+files+from+ZipInputStream
//
// An archive is untrusted input. Entry names can contain "../" and point
// outside the extraction directory ("zip slip"), the declared size of an
// entry can be a lie, and a small, highly compressed entry can expand into
// gigabytes (a zip bomb) or the archive can hold huge numbers of entries.
// Safe extraction resolves each name against the target directory and
// rejects anything that normalizes to a path outside it, counts the bytes
// actually decompressed and stops at a limit, and caps the number of
// entries. This demo builds the hostile archive in memory and extracts into
// a temporary directory.
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class IDS04_ZipExtraction {
    static final long MAX_TOTAL_BYTES = 1_000_000; // 1 MB
    static final int MAX_ENTRIES = 100;

    public static void main(String[] args) throws IOException {
        byte[] zip = hostileArchive();
        System.out.println("archive is smaller than 100 KiB: " + (zip.length < 100 * 1024));

        Path tmp = Files.createTempDirectory("ids04");
        try {
            Path out = Files.createDirectory(tmp.resolve("noncompliant"));
            extractNoncompliant(new ByteArrayInputStream(zip), out);
            System.out.println("noncompliant: wrote outside the target? " + Files.exists(tmp.resolve("evil.sh"))
                    + ", bomb.bin on disk: " + Files.size(out.resolve("bomb.bin")) / 1_000_000 + " MB");

            Path safe = Files.createDirectory(tmp.resolve("compliant"));
            try {
                extractCompliant(new ByteArrayInputStream(zip), safe);
            } catch (IOException e) {
                System.out.println("compliant: stopped: " + e.getMessage());
            }
            try {
                extractCompliant(new ByteArrayInputStream(zipOf("notes/../../evil.sh", new byte[0])), safe);
            } catch (IOException e) {
                System.out.println("compliant: stopped: " + e.getMessage());
            }
        } finally {
            try (Stream<Path> files = Files.walk(tmp)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    // Noncompliant: trusts entry names and sizes
    static void extractNoncompliant(InputStream in, Path dir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path target = dir.resolve(entry.getName());
                Files.createDirectories(target.getParent());
                Files.copy(zis, target);
            }
        }
    }

    // Compliant: confines paths, counts real bytes, caps entries
    static void extractCompliant(InputStream in, Path dir) throws IOException {
        Path root = dir.toAbsolutePath().normalize();
        long total = 0;
        int entries = 0;
        try (ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw new IOException("too many entries");
                }
                Path target = root.resolve(entry.getName()).normalize();
                if (!target.startsWith(root)) {
                    throw new IOException("entry outside target directory: " + entry.getName());
                }
                Files.createDirectories(target.getParent());
                try (OutputStream os = Files.newOutputStream(target)) {
                    int n;
                    while ((n = zis.read(buffer)) != -1) {
                        total += n;
                        if (total > MAX_TOTAL_BYTES) {
                            throw new IOException("archive expands beyond " + MAX_TOTAL_BYTES + " bytes in " + entry.getName());
                        }
                        os.write(buffer, 0, n);
                    }
                }
            }
        }
    }

    // A small archive: 20 MB of zeros and a path traversal entry
    static byte[] hostileArchive() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bytes)) {
            zos.putNextEntry(new ZipEntry("readme.txt"));
            zos.write("hello".getBytes());
            zos.putNextEntry(new ZipEntry("bomb.bin"));
            byte[] zeros = new byte[1_000_000];
            for (int i = 0; i < 20; i++) {
                zos.write(zeros);
            }
            zos.putNextEntry(new ZipEntry("../evil.sh"));
            zos.write("echo pwned".getBytes());
        }
        return bytes.toByteArray();
    }

    static byte[] zipOf(String name, byte[] content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bytes)) {
            zos.putNextEntry(new ZipEntry(name));
            zos.write(content);
        }
        return bytes.toByteArray();
    }
}

/* Output:
archive is smaller than 100 KiB: true
noncompliant: wrote outside the target? true, bomb.bin on disk: 20 MB
compliant: stopped: archive expands beyond 1000000 bytes in bomb.bin
compliant: stopped: entry outside target directory: notes/../../evil.sh
*/
