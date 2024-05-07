// SHA-3: a different design as a hedge, checked against FIPS 202 vectors
//
// SHA-1 and SHA-2 share the Merkle-Damgard design: the digest IS the final
// internal state, which enables length-extension attacks (see
// LengthExtension.java). SHA-3 (FIPS 202, Keccak) is a sponge: the state is
// 1600 bits, only part of it is ever output, so the digest does not reveal
// the state and SHA3-256(secret || m) is not extendable. SHA-2 is not broken;
// SHA-3 exists so that a future break of one family does not take out the
// other. Java has had SHA3-224/256/384/512 MessageDigests since Java 9 and
// HmacSHA3-* since Java 16. Choose by interoperability: SHA-256 is still the
// default almost everywhere.
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public class Sha3KnownAnswer {
    public static void main(String[] args) throws Exception {
        // FIPS 202 example values (NIST CSRC "SHA3-256 Msg0" and "abc")
        kat("SHA3-256", "", "a7ffc6f8bf1ed76651c14756a061d662f580ff4de43b49fa82d80a4b80f8434a");
        kat("SHA3-256", "abc", "3a985da74fe225b2045c172d6bd390bd855f086e3e9d525b46bfe24511431532");
        kat("SHA3-512", "abc", "b751850b1a57168a5693cd924b6b096e08f621827444f70d884f5d0240d2712e"
                + "10e116e9192af3c91a7ec57647e3934057340b4cf408d5a56592f8274eec53f0");

        // Same output length, unrelated values: SHA3-256 is not "SHA-256 v3"
        byte[] abc = "abc".getBytes(StandardCharsets.US_ASCII);
        byte[] sha2 = MessageDigest.getInstance("SHA-256").digest(abc);
        byte[] sha3 = MessageDigest.getInstance("SHA3-256").digest(abc);
        System.out.println("SHA-256 and SHA3-256 both 32 bytes: " + (sha2.length == 32 && sha3.length == 32));
        System.out.println("SHA-256(abc) equals SHA3-256(abc): " + MessageDigest.isEqual(sha2, sha3));

        // Rate (bytes absorbed per permutation) = 200 - 2 * output length
        for (int bits : new int[] {224, 256, 384, 512}) {
            System.out.printf("SHA3-%d: capacity %d bits, rate %d bytes%n", bits, 2 * bits, 200 - 2 * bits / 8);
        }
    }

    static void kat(String alg, String msg, String expectedHex) throws Exception {
        String hex = HexFormat.of().formatHex(MessageDigest.getInstance(alg).digest(msg.getBytes(StandardCharsets.US_ASCII)));
        System.out.println(alg + "(\"" + msg + "\") = " + hex.substring(0, 16) + "... " + (hex.equals(expectedHex) ? "OK" : "MISMATCH"));
    }
}

/* Output:
SHA3-256("") = a7ffc6f8bf1ed766... OK
SHA3-256("abc") = 3a985da74fe225b2... OK
SHA3-512("abc") = b751850b1a57168a... OK
SHA-256 and SHA3-256 both 32 bytes: true
SHA-256(abc) equals SHA3-256(abc): false
SHA3-224: capacity 448 bits, rate 144 bytes
SHA3-256: capacity 512 bits, rate 136 bytes
SHA3-384: capacity 768 bits, rate 104 bytes
SHA3-512: capacity 1024 bits, rate 72 bytes
*/
