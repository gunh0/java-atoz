// Misuse: verifying a MAC with String.equals, startsWith or a short-circuit loop
//
// Computing the right HMAC is half the job; comparing it is the other half.
// Two classic bugs. (1) Early-exit comparison (String.equals on hex,
// Arrays.equals, a hand-written loop that returns at the first mismatch):
// the time taken grows with the length of the correct prefix, so an attacker
// who can measure it fixes the tag one byte at a time, about 256 tries per
// byte instead of 2^256 in total (remote timing attacks of this kind have
// been demonstrated against real web frameworks and devices).
// (2) Comparing only what the client sent, e.g. expectedHex.startsWith(tag):
// an empty or one-character "tag" passes. CERT EXP02-J
// (../../secure-coding/EXP/EXP02_ArraysEquals.java) covers array equality in
// general. The fix: decode to bytes, require the exact length, compare with
// MessageDigest.isEqual, which examines every byte regardless of mismatches.
// Timing is simulated here by counting compared bytes, so output is stable.
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class MacComparison {
    static int bytesCompared; // stands in for the time an attacker would measure

    public static void main(String[] args) throws Exception {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        byte[] msg = "amount=100&to=alice".getBytes(StandardCharsets.US_ASCII);
        byte[] tag = hmac(key, msg);

        // (1) Early exit leaks the length of the correct prefix
        System.out.println("early-exit compare, work per guess:");
        for (int correctPrefix : new int[] {0, 1, 2, 8}) {
            byte[] guess = new byte[32];
            System.arraycopy(tag, 0, guess, 0, correctPrefix);
            guess[correctPrefix] = (byte) ~tag[correctPrefix]; // first wrong byte
            bytesCompared = 0;
            naiveEquals(tag, guess);
            System.out.printf("  guess with %d correct leading bytes -> %d bytes compared%n", correctPrefix, bytesCompared);
        }
        System.out.println("constant-time compare, work per guess: always " + tag.length + " bytes");

        // (2) Prefix check on hex strings: the empty string is a prefix of everything
        String expectedHex = HexFormat.of().formatHex(tag);
        System.out.println();
        System.out.println("startsWith check accepts empty tag: " + expectedHex.startsWith(""));
        System.out.println("startsWith check accepts a 1-digit tag (a 1-in-16 guess): "
                + expectedHex.startsWith(expectedHex.substring(0, 1)));

        // Compliant verifier
        System.out.println("compliant, genuine tag:   " + verify(key, msg, expectedHex));
        System.out.println("compliant, empty tag:     " + verify(key, msg, ""));
        System.out.println("compliant, truncated tag: " + verify(key, msg, expectedHex.substring(0, 32)));
        System.out.println("compliant, upper-case hex of the genuine tag: " + verify(key, msg, expectedHex.toUpperCase()));
    }

    // Noncompliant: returns at the first difference
    static boolean naiveEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            bytesCompared++;
            if (a[i] != b[i]) return false;
        }
        return true;
    }

    // Compliant: parse, check exact length, constant-time compare
    static boolean verify(byte[] key, byte[] msg, String receivedHex) throws Exception {
        byte[] received;
        try {
            received = HexFormat.of().parseHex(receivedHex); // accepts either case
        } catch (IllegalArgumentException e) {
            return false;
        }
        byte[] expected = hmac(key, msg);
        return received.length == expected.length && MessageDigest.isEqual(expected, received);
    }

    static byte[] hmac(byte[] key, byte[] msg) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(msg);
    }
}

/* Output:
early-exit compare, work per guess:
  guess with 0 correct leading bytes -> 1 bytes compared
  guess with 1 correct leading bytes -> 2 bytes compared
  guess with 2 correct leading bytes -> 3 bytes compared
  guess with 8 correct leading bytes -> 9 bytes compared
constant-time compare, work per guess: always 32 bytes

startsWith check accepts empty tag: true
startsWith check accepts a 1-digit tag (a 1-in-16 guess): true
compliant, genuine tag:   true
compliant, empty tag:     false
compliant, truncated tag: false
compliant, upper-case hex of the genuine tag: true
*/
