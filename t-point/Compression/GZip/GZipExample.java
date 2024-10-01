// GZIPOutputStream / GZIPInputStream (java.util.zip) are stream decorators:
// wrap any OutputStream to compress what you write, any InputStream to
// decompress what you read. Here everything stays in memory with
// ByteArrayOutputStream / ByteArrayInputStream.
//  - close() (or finish()) on the GZIPOutputStream writes the trailer
//    (CRC-32 + size); forget it and the data is truncated
//  - a gzip stream starts with the magic bytes 1f 8b and method 08 (deflate)
//  - repetitive data compresses well, random-looking data does not
//  - when decompressing untrusted input, cap the output size: a few KiB of
//    gzip can expand to gigabytes (a "zip bomb")
// Exact compressed sizes depend on the zlib build, so this prints ratios.
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipException;

public class GZipExample {

    static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (OutputStream out = new GZIPOutputStream(buffer)) {
            out.write(data);
        } // close() writes the trailer
        return buffer.toByteArray();
    }

    static byte[] gunzip(byte[] compressed) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return in.readAllBytes();
        }
    }

    // Decompress with an upper bound on the output size
    static byte[] gunzipLimited(byte[] compressed, int maxBytes) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] result = in.readNBytes(maxBytes);
            if (in.read() != -1) {
                throw new IOException("decompressed data exceeds " + maxBytes + " bytes");
            }
            return result;
        }
    }

    public static void main(String[] args) throws IOException {
        // Repetitive: a log with the same line over and over
        byte[] log = "INFO GET /health 200 1ms\n".repeat(2000).getBytes(StandardCharsets.US_ASCII);
        byte[] gzLog = gzip(log);
        System.out.printf("log: %d bytes, compressed below 2%%: %b%n", log.length, gzLog.length * 50 < log.length);
        System.out.printf("magic: %02x %02x, method: %02x%n", gzLog[0], gzLog[1], gzLog[2]);
        System.out.println("round trip equal: " + Arrays.equals(log, gunzip(gzLog)));

        // Random-looking bytes barely compress (gzip may even make them larger)
        byte[] noise = new byte[50_000];
        new Random(42).nextBytes(noise);
        byte[] gzNoise = gzip(noise);
        System.out.printf("noise: %d bytes, compressed still above 99%%: %b%n",
                noise.length, gzNoise.length * 100L > noise.length * 99L);

        // Truncated stream (trailer missing) is detected on read
        byte[] truncated = Arrays.copyOf(gzLog, gzLog.length - 8);
        try {
            gunzip(truncated);
        } catch (EOFException e) {
            System.out.println("truncated gzip -> " + e.getClass().getSimpleName());
        }

        // Not gzip at all
        try {
            gunzip("plain text".getBytes(StandardCharsets.US_ASCII));
        } catch (ZipException e) {
            System.out.println("plain text -> " + e.getClass().getSimpleName());
        }

        // Decompression bomb guard: 10 MB of zeros compresses to a few KiB
        byte[] bomb = gzip(new byte[10_000_000]);
        System.out.println("bomb compressed below 20 KiB: " + (bomb.length < 20_000));
        try {
            gunzipLimited(bomb, 1_000_000);
        } catch (IOException e) {
            System.out.println("limited gunzip -> " + e.getMessage());
        }
        System.out.println("limited gunzip of the log ok: " + Arrays.equals(log, gunzipLimited(gzLog, 1_000_000)));
    }
}

/* Output:
log: 50000 bytes, compressed below 2%: true
magic: 1f 8b, method: 08
round trip equal: true
noise: 50000 bytes, compressed still above 99%: true
truncated gzip -> EOFException
plain text -> ZipException
bomb compressed below 20 KiB: true
limited gunzip -> decompressed data exceeds 1000000 bytes
limited gunzip of the log ok: true
*/
