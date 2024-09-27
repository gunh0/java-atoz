// java.util.HexFormat (Java 17) converts between bytes and hexadecimal text,
// replacing hand-written loops with String.format("%02x") or the
// Integer.toHexString(b & 0xff) dance.
//  - HexFormat.of() formats lower case with no delimiter; withUpperCase(),
//    withDelimiter(":"), withPrefix/withSuffix customise each byte
//  - ofDelimiter(":") is a shortcut for of().withDelimiter(":")
//  - parseHex(text) is the inverse and must match the same format; it throws
//    IllegalArgumentException on odd length or non-hex characters
//  - toHexDigits(value) prints a primitive with fixed width; fromHexDigits
//    parses one
// HexFormat instances are immutable and thread-safe.
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

public class HexFormatExample {
    public static void main(String[] args) throws NoSuchAlgorithmException {
        byte[] bytes = {0x00, 0x1a, (byte) 0xff, 0x7f, (byte) 0x80};

        HexFormat hex = HexFormat.of();
        System.out.println("plain:      " + hex.formatHex(bytes));
        System.out.println("upper:      " + hex.withUpperCase().formatHex(bytes));
        System.out.println("MAC style:  " + HexFormat.ofDelimiter(":").withUpperCase().formatHex(bytes));
        System.out.println("C array:    {" + HexFormat.ofDelimiter(", ").withPrefix("0x").formatHex(bytes) + "}");

        // Parsing back (case-insensitive)
        byte[] parsed = hex.parseHex("001AFF7F80");
        System.out.println("parsed equal: " + Arrays.equals(bytes, parsed));
        byte[] mac = HexFormat.ofDelimiter(":").parseHex("00:1a:ff:7f:80");
        System.out.println("parsed MAC equal: " + Arrays.equals(bytes, mac));

        for (String bad : new String[] {"abc", "zz", "00:1a"}) {
            try {
                hex.parseHex(bad);
                System.out.println("accepted " + bad);
            } catch (IllegalArgumentException e) {
                System.out.println("rejected " + bad);
            }
        }

        // Fixed-width primitive formatting
        System.out.println("toHexDigits(byte 10):   " + hex.toHexDigits((byte) 10));
        System.out.println("toHexDigits(short -1):  " + hex.toHexDigits((short) -1));
        System.out.println("toHexDigits(int 255):   " + hex.toHexDigits(255));
        System.out.println("toHexDigits(long 1L):   " + hex.toHexDigits(1L));
        System.out.println("fromHexDigits(\"7fff\"): " + HexFormat.fromHexDigits("7fff"));
        System.out.println("isHexDigit('g'): " + HexFormat.isHexDigit('g'));

        // Common use: printing a digest (SHA-256 of "abc", a FIPS 180-2 test vector)
        byte[] digest = MessageDigest.getInstance("SHA-256").digest("abc".getBytes(StandardCharsets.US_ASCII));
        System.out.println("sha256(abc): " + hex.formatHex(digest));

        // Range formatting: just the first 4 bytes
        System.out.println("prefix:      " + hex.formatHex(digest, 0, 4));
    }
}

/* Output:
plain:      001aff7f80
upper:      001AFF7F80
MAC style:  00:1A:FF:7F:80
C array:    {0x00, 0x1a, 0xff, 0x7f, 0x80}
parsed equal: true
parsed MAC equal: true
rejected abc
rejected zz
rejected 00:1a
toHexDigits(byte 10):   0a
toHexDigits(short -1):  ffff
toHexDigits(int 255):   000000ff
toHexDigits(long 1L):   0000000000000001
fromHexDigits("7fff"): 32767
isHexDigit('g'): false
sha256(abc): ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
prefix:      ba7816bf
*/
