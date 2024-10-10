// Below the gzip and zip streams sit two lower-level pieces of java.util.zip:
//  - Deflater / Inflater: the raw DEFLATE codec working on byte arrays.
//    Feed input with setInput(), call finish(), and drain deflate() until
//    finished(); Inflater mirrors it. nowrap=true gives a raw stream without
//    the zlib header, the format zip and gzip embed. Always call end() to
//    free the native memory.
//  - Checksum implementations: CRC32 (zip, gzip, PNG), CRC32C (Java 9,
//    hardware-accelerated, used by iSCSI and ext4) and Adler32 (zlib).
//    They detect accidental corruption only; anyone can forge a matching
//    checksum, so use a MAC or signature against tampering.
// The check value is each algorithm's checksum of the ASCII text "123456789".
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.CRC32C;
import java.util.zip.Checksum;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class ChecksumsExample {

    static byte[] deflate(byte[] input, int level, boolean nowrap) {
        Deflater deflater = new Deflater(level, nowrap);
        try {
            deflater.setInput(input);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[256];
            while (!deflater.finished()) {
                int n = deflater.deflate(chunk);
                out.write(chunk, 0, n);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    static byte[] inflate(byte[] compressed, boolean nowrap) throws DataFormatException {
        Inflater inflater = new Inflater(nowrap);
        try {
            inflater.setInput(compressed);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[256];
            while (!inflater.finished()) {
                int n = inflater.inflate(chunk);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new DataFormatException("truncated input");
                }
                out.write(chunk, 0, n);
            }
            return out.toByteArray();
        } finally {
            inflater.end();
        }
    }

    static String hex(Checksum c) {
        return String.format("%08x", c.getValue());
    }

    public static void main(String[] args) throws DataFormatException {
        byte[] check = "123456789".getBytes(StandardCharsets.US_ASCII);
        Checksum[] algorithms = {new CRC32(), new CRC32C(), new Adler32()};
        for (Checksum c : algorithms) {
            c.update(check);
            System.out.printf("%-8s %s%n", c.getClass().getSimpleName(), hex(c));
        }

        // Incremental updates give the same result as one big update
        CRC32 incremental = new CRC32();
        incremental.update("1234".getBytes(StandardCharsets.US_ASCII));
        incremental.update(ByteBuffer.wrap("56789".getBytes(StandardCharsets.US_ASCII)));
        System.out.println("incremental CRC32 " + hex(incremental));

        // A single flipped bit changes the checksum
        byte[] flipped = check.clone();
        flipped[4] ^= 0x01;
        CRC32 damaged = new CRC32();
        damaged.update(flipped);
        System.out.println("one bit flipped   " + hex(damaged));
        incremental.reset();
        System.out.println("after reset       " + hex(incremental));

        // Raw Deflater / Inflater round trip
        byte[] text = "the quick brown fox jumps over the lazy dog. ".repeat(40).getBytes(StandardCharsets.US_ASCII);
        for (boolean nowrap : new boolean[] {false, true}) {
            byte[] packed = deflate(text, Deflater.BEST_COMPRESSION, nowrap);
            byte[] unpacked = inflate(packed, nowrap);
            System.out.printf("nowrap=%-5b compressed below 10%%: %b, round trip: %b%n",
                    nowrap, packed.length * 10 < text.length, Arrays.equals(text, unpacked));
        }
        // zlib streams start with 0x78 (deflate, 32K window)
        System.out.printf("zlib header byte: %02x%n", deflate(text, Deflater.DEFAULT_COMPRESSION, false)[0]);

        // NO_COMPRESSION still wraps the data in deflate blocks: slightly larger
        byte[] stored = deflate(text, Deflater.NO_COMPRESSION, true);
        System.out.println("NO_COMPRESSION larger than input: " + (stored.length > text.length));

        // Garbage input
        try {
            inflate("definitely not deflate".getBytes(StandardCharsets.US_ASCII), false);
        } catch (DataFormatException e) {
            System.out.println("garbage -> " + e.getClass().getSimpleName());
        }
    }
}

/* Output:
CRC32    cbf43926
CRC32C   e3069283
Adler32  091e01de
incremental CRC32 cbf43926
one bit flipped   f6941096
after reset       00000000
nowrap=false compressed below 10%: true, round trip: true
nowrap=true  compressed below 10%: true, round trip: true
zlib header byte: 78
NO_COMPRESSION larger than input: true
garbage -> DataFormatException
*/
