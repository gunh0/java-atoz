// MessageDigest and SHA-2, checked against the FIPS 180 test vectors
//
// A cryptographic hash maps any input to a fixed-size digest and should be
// preimage resistant (given h, find m with H(m) = h), second-preimage
// resistant and collision resistant (find any m1 != m2 with equal digests;
// generic cost 2^(n/2), i.e. 2^128 for SHA-256). It has no key, so it gives
// integrity only against accidental change or when the digest itself comes
// over a trusted channel; against an attacker you need a MAC or signature.
// MessageDigest is stateful: update() feeds data, digest() finishes and
// resets. Known-answer tests (KATs) from the standard are how you check
// that a provider, or your own use of it, is correct.
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public class Sha2KnownAnswer {
    static final HexFormat HEX = HexFormat.of();

    public static void main(String[] args) throws Exception {
        // FIPS 180-2 Appendix B / C examples
        kat("SHA-256", "abc", "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        kat("SHA-256", "", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        kat("SHA-256", "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq",
                "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1");
        kat("SHA-512", "abc", "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a"
                + "2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f");

        // One million 'a's, fed in 1000 chunks: update() is incremental, so
        // large inputs (files, streams) never have to be in memory at once.
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] chunk = "a".repeat(1000).getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < 1000; i++) md.update(chunk);
        check("SHA-256(10^6 x 'a')", md.digest(), "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0");

        // digest() resets the object: the next digest starts from scratch
        md.update("abc".getBytes(StandardCharsets.US_ASCII));
        System.out.println("reused after reset matches \"abc\": "
                + HEX.formatHex(md.digest()).startsWith("ba7816bf"));

        // Avalanche: one changed input bit changes about half of the output bits
        byte[] h1 = sha256("abc".getBytes(StandardCharsets.US_ASCII));
        byte[] h2 = sha256("abb".getBytes(StandardCharsets.US_ASCII)); // 'c' = 0x63, 'b' = 0x62: one bit
        int diff = 0;
        for (int i = 0; i < h1.length; i++) diff += Integer.bitCount((h1[i] ^ h2[i]) & 0xff);
        System.out.println("\"abc\" vs \"abb\": " + diff + " of 256 output bits differ");
        System.out.println("digest length SHA-256 / SHA-384 / SHA-512: "
                + MessageDigest.getInstance("SHA-256").getDigestLength() + " / "
                + MessageDigest.getInstance("SHA-384").getDigestLength() + " / "
                + MessageDigest.getInstance("SHA-512").getDigestLength() + " bytes");
    }

    static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    static void kat(String alg, String msg, String expectedHex) throws Exception {
        byte[] d = MessageDigest.getInstance(alg).digest(msg.getBytes(StandardCharsets.US_ASCII));
        String label = alg + "(\"" + (msg.length() > 10 ? msg.substring(0, 10) + "..." : msg) + "\")";
        check(label, d, expectedHex);
    }

    static void check(String label, byte[] digest, String expectedHex) {
        String hex = HEX.formatHex(digest);
        System.out.println(label + " = " + hex.substring(0, 16) + "... " + (hex.equals(expectedHex) ? "OK" : "MISMATCH"));
    }
}

/* Output:
SHA-256("abc") = ba7816bf8f01cfea... OK
SHA-256("") = e3b0c44298fc1c14... OK
SHA-256("abcdbcdecd...") = 248d6a61d20638b8... OK
SHA-512("abc") = ddaf35a193617aba... OK
SHA-256(10^6 x 'a') = cdc76e5c9914fb92... OK
reused after reset matches "abc": true
"abc" vs "abb": 124 of 256 output bits differ
digest length SHA-256 / SHA-384 / SHA-512: 32 / 48 / 64 bytes
*/
