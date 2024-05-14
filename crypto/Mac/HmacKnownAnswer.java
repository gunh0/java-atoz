// HMAC: message authentication with a shared key (RFC 2104, vectors RFC 4231)
//
// A MAC proves that a message came from someone holding the key and was not
// modified. HMAC(K, m) = H((K ^ opad) || H((K ^ ipad) || m)) turns any hash
// into a MAC without the length-extension problem of H(K || m). Rules of use:
// the key should be random and at least as long as the hash output (keys
// longer than the hash's block size are hashed first, so they add nothing);
// a tag may be truncated (never below 128 bits for general use); verify with
// a constant-time comparison such as MessageDigest.isEqual, never with
// Arrays.equals or String.equals on hex, which stop at the first mismatching
// byte and leak, through timing, how much of a guessed tag was right.
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HmacKnownAnswer {
    static final HexFormat HEX = HexFormat.of();

    public static void main(String[] args) throws Exception {
        // RFC 4231 test case 1: 20-byte key 0x0b..0b, data "Hi There"
        byte[] key1 = filled(20, 0x0b);
        kat("TC1 HmacSHA256", "HmacSHA256", key1, "Hi There",
                "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7");
        kat("TC1 HmacSHA512", "HmacSHA512", key1, "Hi There",
                "87aa7cdea5ef619d4ff0b4241a1d6cb02379f4e2ce4ec2787ad0b30545e17cde"
                + "daa833b7d6b8a702038b274eaea3f4e4be9d914eeb61f1702e696c203a126854");
        // Test case 2: a short, human key ("Jefe") is legal but weak
        kat("TC2 HmacSHA256", "HmacSHA256", "Jefe".getBytes(StandardCharsets.US_ASCII),
                "what do ya want for nothing?",
                "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
        // Test case 5: output truncated to 128 bits
        byte[] full = hmac("HmacSHA256", filled(20, 0x0c), "Test With Truncation");
        System.out.println("TC5 truncated to 128 bits: "
                + (HEX.formatHex(Arrays.copyOf(full, 16)).equals("a3b6167473100ee06e0c796c2955552b") ? "OK" : "MISMATCH"));
        // Test case 6: a 131-byte key is longer than SHA-256's 64-byte block
        byte[] longKey = filled(131, 0xaa);
        String msg6 = "Test Using Larger Than Block-Size Key - Hash Key First";
        kat("TC6 HmacSHA256", "HmacSHA256", longKey, msg6,
                "60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54");
        // ...which is why HMAC(K) == HMAC(SHA-256(K)) for such keys
        byte[] hashedKey = MessageDigest.getInstance("SHA-256").digest(longKey);
        System.out.println("long key equivalent to its hash: "
                + MessageDigest.isEqual(hmac("HmacSHA256", longKey, msg6), hmac("HmacSHA256", hashedKey, msg6)));

        // Verification: recompute, then compare in constant time
        byte[] tag = hmac("HmacSHA256", key1, "Hi There");
        System.out.println();
        System.out.println("verify genuine tag:  " + verify(key1, "Hi There", tag));
        System.out.println("verify altered data: " + verify(key1, "Hi there", tag));
        byte[] flipped = tag.clone();
        flipped[31] ^= 1;
        System.out.println("verify flipped bit:  " + verify(key1, "Hi There", flipped));
    }

    static boolean verify(byte[] key, String data, byte[] receivedTag) throws Exception {
        byte[] expected = hmac("HmacSHA256", key, data);
        return MessageDigest.isEqual(expected, receivedTag); // time independent of mismatch position
    }

    static byte[] hmac(String alg, byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance(alg);
        mac.init(new SecretKeySpec(key, alg));
        return mac.doFinal(data.getBytes(StandardCharsets.US_ASCII));
    }

    static void kat(String label, String alg, byte[] key, String data, String expectedHex) throws Exception {
        String hex = HEX.formatHex(hmac(alg, key, data));
        System.out.println(label + " = " + hex.substring(0, 16) + "... " + (hex.equals(expectedHex) ? "OK" : "MISMATCH"));
    }

    static byte[] filled(int len, int value) {
        byte[] b = new byte[len];
        Arrays.fill(b, (byte) value);
        return b;
    }
}

/* Output:
TC1 HmacSHA256 = b0344c61d8db3853... OK
TC1 HmacSHA512 = 87aa7cdea5ef619d... OK
TC2 HmacSHA256 = 5bdcc146bf60754e... OK
TC5 truncated to 128 bits: OK
TC6 HmacSHA256 = 60e431591ee0b67f... OK
long key equivalent to its hash: true

verify genuine tag:  true
verify altered data: false
verify flipped bit:  false
*/
