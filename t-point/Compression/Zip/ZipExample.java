// ZipOutputStream / ZipInputStream read and write zip archives as streams;
// here the archive lives in a byte array, so nothing touches the disk.
//  - putNextEntry(entry) starts a file, write() its content, closeEntry()
//    ends it; a name ending in "/" is a directory entry
//  - DEFLATED (default) compresses; STORED keeps bytes as they are, but then
//    size, compressed size and CRC-32 must be set on the entry up front
//  - reading: loop getNextEntry() until null; the stream returns the
//    current entry's bytes until its end
// Entry names come from whoever built the archive. Before extracting,
// resolve each name against the target directory and reject the ones that
// escape it ("zip slip": ../../etc/cron.d/job). The secure-coding section
// covers this in depth.
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipExample {

    static void addDeflated(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    static void addStored(ZipOutputStream zip, String name, byte[] content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        CRC32 crc = new CRC32();
        crc.update(content);
        entry.setSize(content.length);
        entry.setCompressedSize(content.length);
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        zip.write(content);
        zip.closeEntry();
    }

    static byte[] buildArchive() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            zip.setComment("demo archive");
            zip.putNextEntry(new ZipEntry("docs/"));
            zip.closeEntry();
            addDeflated(zip, "docs/readme.txt", "Read me first.\n".repeat(50));
            addDeflated(zip, "config/app.properties", "port=8080\nmode=prod\n");
            // Already-compressed data (images, jars) is often STORED
            addStored(zip, "img/logo.png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a});
            // A malicious name: legal in a zip, dangerous when extracted naively
            addDeflated(zip, "../../outside.txt", "gotcha");
        }
        return buffer.toByteArray();
    }

    // Returns the target path, or null if the entry would escape the directory
    static Path safeTarget(Path targetDir, String entryName) {
        Path resolved = targetDir.resolve(entryName).normalize();
        return resolved.startsWith(targetDir) ? resolved : null;
    }

    public static void main(String[] args) throws IOException {
        byte[] archive = buildArchive();
        System.out.printf("archive magic: %c%c %02x %02x%n", archive[0], archive[1], archive[2], archive[3]);

        Path targetDir = Path.of("/srv/extract"); // only used for path arithmetic, nothing is written
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                byte[] content = zip.readAllBytes(); // reads up to the end of this entry
                String method = entry.getMethod() == ZipEntry.STORED ? "STORED" : "DEFLATED";
                String kind = entry.isDirectory() ? "dir " : "file";
                Path target = safeTarget(targetDir, entry.getName());
                System.out.printf("%s %-22s %-8s %4d bytes  %s%n", kind, entry.getName(), method, content.length,
                        target == null ? "REJECTED (escapes target dir)" : "-> " + targetDir.relativize(target));
            }
        }

        // The CRC-32 stored in the archive is checked while reading:
        // flip one content byte of the STORED entry and reading it fails.
        byte[] corrupted = archive.clone();
        int png = indexOf(corrupted, new byte[] {'P', 'N', 'G', 0x0d});
        corrupted[png] = 'X';
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(corrupted))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                zip.readAllBytes();
            }
            System.out.println("corruption not detected");
        } catch (ZipException e) {
            System.out.println("corrupted entry -> " + e.getClass().getSimpleName());
        }
    }

    static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}

/* Output:
archive magic: PK 03 04
dir  docs/                  DEFLATED    0 bytes  -> docs
file docs/readme.txt        DEFLATED  750 bytes  -> docs/readme.txt
file config/app.properties  DEFLATED   20 bytes  -> config/app.properties
file img/logo.png           STORED      8 bytes  -> img/logo.png
file ../../outside.txt      DEFLATED    6 bytes  REJECTED (escapes target dir)
corrupted entry -> ZipException
*/
