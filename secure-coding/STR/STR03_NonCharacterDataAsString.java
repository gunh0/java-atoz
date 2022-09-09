// STR03-J. Do not encode noncharacter data as a string
// https://wiki.sei.cmu.edu/confluence/display/java/STR03-J.+Do+not+encode+noncharacter+data+as+a+string
//
// new String(bytes, charset) DECODES bytes as text. Binary data (keys,
// hashes, signatures, IVs, serialized numbers) is usually not valid in the
// charset, and invalid sequences are silently replaced with U+FFFD, so
// converting back with getBytes() gives different bytes. A key stored this
// way no longer decrypts, a signature no longer verifies, a number comes
// back as a different number. Keep binary data in byte[] (or ByteBuffer),
// and when text is required, use an encoding meant for bytes: Base64 or hex.
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

public class STR03_NonCharacterDataAsString {
    public static void main(String[] args) {
        BigInteger x = new BigInteger("530500452766");
        byte[] bytes = x.toByteArray();
        System.out.println("original bytes:      " + HexFormat.ofDelimiter(" ").formatHex(bytes));

        // Noncompliant: bytes -> String -> bytes
        String asText = new String(bytes, StandardCharsets.UTF_8);
        byte[] roundTrip = asText.getBytes(StandardCharsets.UTF_8);
        System.out.println("via new String:      " + HexFormat.ofDelimiter(" ").formatHex(roundTrip));
        System.out.println("noncompliant value:  " + new BigInteger(roundTrip) + " (expected " + x + ")");

        // Compliant: Base64 text that maps back to the very same bytes
        String b64 = Base64.getEncoder().encodeToString(bytes);
        byte[] decoded = Base64.getDecoder().decode(b64);
        System.out.println("Base64 text:         " + b64);
        System.out.println("compliant value:     " + new BigInteger(decoded) + ", bytes equal: " + Arrays.equals(bytes, decoded));

        // Compliant for numbers: use the number's own text form
        String decimal = x.toString();
        System.out.println("BigInteger.toString: " + decimal + " -> " + new BigInteger(decimal).equals(x));

        // Same problem with a key: 16 bytes in, a different-length array out
        byte[] key = HexFormat.of().parseHex("8f3ac1d2e4b5960718293a4b5c6d7e8f");
        byte[] keyRoundTrip = new String(key, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
        System.out.println("key length " + key.length + " -> " + keyRoundTrip.length
                + " after a String round trip, equal: " + Arrays.equals(key, keyRoundTrip));
    }
}

/* Output:
original bytes:      7b 84 4a 81 9e
via new String:      7b ef bf bd 4a ef bf bd ef bf bd
noncompliant value:  149830058370101340468658109 (expected 530500452766)
Base64 text:         e4RKgZ4=
compliant value:     530500452766, bytes equal: true
BigInteger.toString: 530500452766 -> true
key length 16 -> 24 after a String round trip, equal: false
*/
