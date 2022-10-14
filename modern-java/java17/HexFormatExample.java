// Java 17 — java.util.HexFormat (API addition, no JEP)
//
// Converting bytes to hex text is everywhere: digests, keys, MAC addresses,
// protocol dumps. Before Java 17 there was no public JDK API for it, so code
// used String.format("%02x") in a loop, Integer.toHexString with manual
// padding (and the classic sign-extension bug for negative bytes), or the
// internal javax.xml.bind.DatatypeConverter that was removed in Java 11.
// HexFormat formats and parses byte arrays and primitives, with optional
// delimiter, prefix, suffix and upper case, and it is immutable and
// thread-safe.
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

public class HexFormatExample {
    public static void main(String[] args) throws NoSuchAlgorithmException {
        byte[] bytes = {0x0a, (byte) 0xff, 0x00, (byte) 0x80, 0x7f};

        // Before: easy to get wrong
        StringBuilder buggy = new StringBuilder();
        for (byte b : bytes) {
            buggy.append(Integer.toHexString(b)); // sign extension, no padding
        }
        StringBuilder manual = new StringBuilder();
        for (byte b : bytes) {
            manual.append(String.format("%02x", b));
        }
        System.out.println("Integer.toHexString loop: " + buggy);
        System.out.println("String.format loop:       " + manual);

        // After
        HexFormat hex = HexFormat.of();
        System.out.println("HexFormat.of():           " + hex.formatHex(bytes));
        System.out.println("parsed back equal:        " + Arrays.equals(bytes, hex.parseHex(hex.formatHex(bytes))));

        // Delimiters, prefixes and case
        HexFormat mac = HexFormat.ofDelimiter(":").withUpperCase();
        System.out.println("MAC address:              " + mac.formatHex(new byte[] {0, 0x1b, 0x63, (byte) 0x84, 0x45, (byte) 0xe6}));
        HexFormat cArray = HexFormat.ofDelimiter(", ").withPrefix("0x");
        System.out.println("C array literal:          {" + cArray.formatHex(bytes) + "}");

        // Primitives and single digits
        System.out.println("toHexDigits((short) 513): " + hex.toHexDigits((short) 513));
        System.out.println("toHexDigits(255L):        " + hex.toHexDigits(255L));
        System.out.println("fromHexDigits(\"7fff\"):    " + HexFormat.fromHexDigits("7fff"));
        System.out.println("isHexDigit('g'):          " + HexFormat.isHexDigit('g'));

        // Typical use: printing a digest
        byte[] sha256 = MessageDigest.getInstance("SHA-256").digest("abc".getBytes(StandardCharsets.UTF_8));
        System.out.println("SHA-256(\"abc\"):           " + hex.formatHex(sha256));

        // Invalid input is rejected instead of silently misparsed
        try {
            hex.parseHex("abc");
        } catch (IllegalArgumentException e) {
            System.out.println("parseHex(\"abc\"): " + e.getMessage());
        }
    }
}

/* Output:
Integer.toHexString loop: affffffff0ffffff807f
String.format loop:       0aff00807f
HexFormat.of():           0aff00807f
parsed back equal:        true
MAC address:              00:1B:63:84:45:E6
C array literal:          {0x0a, 0xff, 0x00, 0x80, 0x7f}
toHexDigits((short) 513): 0201
toHexDigits(255L):        00000000000000ff
fromHexDigits("7fff"):    32767
isHexDigit('g'):          false
SHA-256("abc"):           ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
parseHex("abc"): string length not even: 3
*/
