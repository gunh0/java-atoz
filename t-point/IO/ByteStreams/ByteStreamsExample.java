// Byte streams move raw bytes: InputStream reads them, OutputStream writes
// them. Everything else in java.io (character streams, buffering, data and
// object streams) is layered on top of these two abstract classes.
//  - read() returns one byte as 0..255, or -1 at end of stream
//  - read(byte[]) fills a buffer and returns how many bytes it actually read,
//    which may be fewer than the buffer size: always use the returned count
//  - FileInputStream/FileOutputStream for files, ByteArray* for memory
//  - transferTo copies a whole stream; close streams with try-with-resources
// Use byte streams for binary data (images, keys, archives); for text, a
// character stream with an explicit charset.
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

public class ByteStreamsExample {
    // File signatures ("magic numbers") identify a format by its first bytes
    static String sniff(byte[] head) {
        HexFormat hex = HexFormat.of();
        String h = hex.formatHex(head);
        if (h.startsWith("89504e47")) return "PNG image";
        if (h.startsWith("504b0304")) return "ZIP archive";
        if (h.startsWith("7f454c46")) return "ELF executable";
        return "unknown";
    }

    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("bytes");
        Path file = dir.resolve("upload.bin");
        try {
            // Write bytes: a PNG signature followed by some payload
            byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 13};
            try (OutputStream out = new FileOutputStream(file.toFile())) {
                out.write(png);
                out.write(new byte[500]); // 500 zero bytes
            }
            System.out.println("wrote " + Files.size(file) + " bytes to " + dir.relativize(file));

            // Read byte by byte: -1 marks the end, values are 0..255 (not signed)
            try (InputStream in = new FileInputStream(file.toFile())) {
                int first = in.read();
                System.out.println("first byte as int: " + first + " (as Java byte: " + (byte) first + ")");
            }

            // Read in chunks: the returned count matters, the last chunk is short
            int chunks = 0;
            long total = 0;
            byte[] buffer = new byte[128];
            try (InputStream in = new FileInputStream(file.toFile())) {
                int n;
                while ((n = in.read(buffer)) != -1) {
                    chunks++;
                    total += n;
                    if (n < buffer.length) System.out.println("last chunk had " + n + " bytes");
                }
            }
            System.out.println("read " + total + " bytes in " + chunks + " chunks");

            // readNBytes reads exactly n (or until EOF): handy for headers
            try (InputStream in = new FileInputStream(file.toFile())) {
                byte[] head = in.readNBytes(4);
                System.out.println("header " + HexFormat.of().formatHex(head) + " -> " + sniff(head));
            }

            // In-memory streams and transferTo
            InputStream zip = new ByteArrayInputStream(new byte[] {'P', 'K', 3, 4, 20, 0});
            ByteArrayOutputStream copy = new ByteArrayOutputStream();
            long copied = zip.transferTo(copy);
            System.out.println("copied " + copied + " bytes in memory -> " + sniff(copy.toByteArray()));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }
}

/* Output:
wrote 512 bytes to upload.bin
first byte as int: 137 (as Java byte: -119)
read 512 bytes in 4 chunks
header 89504e47 -> PNG image
copied 6 bytes in memory -> ZIP archive
*/
