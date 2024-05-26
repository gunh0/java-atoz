// HKDF: deriving keys from a secret that is already strong (RFC 5869)
//
// A Diffie-Hellman shared secret or a master key is high-entropy but not
// uniformly random, and you usually need several independent keys from it
// (encryption, MAC, one per direction). HKDF does this in two steps:
// Extract, PRK = HMAC(salt, IKM), concentrates the entropy into a
// pseudorandom key; Expand, T(i) = HMAC(PRK, T(i-1) || info || i), stretches
// it to the length you need, and the "info" label binds each output to its
// purpose, so different labels give independent keys. HKDF is NOT for
// passwords (no work factor: use PBKDF2). The JDK has no public HKDF class
// in this release, but it is a few lines over javax.crypto.Mac; the RFC test
// vectors below confirm the implementation.
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class Hkdf {
    static final HexFormat HEX = HexFormat.of();

    static byte[] extract(byte[] salt, byte[] ikm) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        // An absent salt means HashLen zero bytes (RFC 5869, section 2.2)
        mac.init(new SecretKeySpec(salt.length == 0 ? new byte[32] : salt, "HmacSHA256"));
        return mac.doFinal(ikm);
    }

    static byte[] expand(byte[] prk, byte[] info, int length) throws Exception {
        if (length > 255 * 32) throw new IllegalArgumentException("HKDF-SHA256 output is limited to 8160 bytes");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        ByteArrayOutputStream okm = new ByteArrayOutputStream();
        byte[] t = new byte[0];
        for (int i = 1; okm.size() < length; i++) {
            mac.update(t);
            mac.update(info);
            mac.update((byte) i);
            t = mac.doFinal();
            okm.writeBytes(t);
        }
        return Arrays.copyOf(okm.toByteArray(), length);
    }

    public static void main(String[] args) throws Exception {
        // RFC 5869 A.1: basic test case with SHA-256
        byte[] ikm = new byte[22];
        Arrays.fill(ikm, (byte) 0x0b);
        byte[] prk = extract(HEX.parseHex("000102030405060708090a0b0c"), ikm);
        byte[] okm = expand(prk, HEX.parseHex("f0f1f2f3f4f5f6f7f8f9"), 42);
        check("A.1 PRK", prk, "077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5");
        check("A.1 OKM", okm, "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865");

        // RFC 5869 A.3: no salt, no info
        byte[] prk3 = extract(new byte[0], ikm);
        check("A.3 PRK", prk3, "19ef24a32c717b167f33a91d6f648bdf96596776afdb6377ac434c1c293ccb04");
        check("A.3 OKM", expand(prk3, new byte[0], 42),
                "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8");

        // Domain separation: one master secret, independent keys per purpose
        byte[] master = new byte[32];
        new SecureRandom().nextBytes(master);
        byte[] p = extract("app-v1".getBytes(StandardCharsets.UTF_8), master);
        byte[] encKey = expand(p, "client->server encryption".getBytes(StandardCharsets.UTF_8), 32);
        byte[] macKey = expand(p, "client->server mac".getBytes(StandardCharsets.UTF_8), 32);
        System.out.println("enc and mac keys differ: " + !MessageDigest.isEqual(encKey, macKey));
        System.out.println("expanding again is deterministic: "
                + MessageDigest.isEqual(encKey, expand(p, "client->server encryption".getBytes(StandardCharsets.UTF_8), 32)));
        // A shorter output is a prefix of a longer one with the same info,
        // so never derive two keys by asking for different lengths.
        System.out.println("16-byte output is a prefix of the 32-byte one: "
                + Arrays.equals(Arrays.copyOf(encKey, 16), expand(p, "client->server encryption".getBytes(StandardCharsets.UTF_8), 16)));
    }

    static void check(String label, byte[] actual, String expectedHex) {
        System.out.println(label + " " + (HEX.formatHex(actual).equals(expectedHex) ? "OK" : "MISMATCH")
                + " (" + actual.length + " bytes)");
    }
}

/* Output:
A.1 PRK OK (32 bytes)
A.1 OKM OK (42 bytes)
A.3 PRK OK (32 bytes)
A.3 OKM OK (42 bytes)
enc and mac keys differ: true
expanding again is deterministic: true
16-byte output is a prefix of the 32-byte one: true
*/
