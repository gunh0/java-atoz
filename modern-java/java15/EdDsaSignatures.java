// JEP 339 — Edwards-Curve Digital Signature Algorithm (EdDSA) (Java 15)
//
// ECDSA, the elliptic-curve signature the JDK already had, needs a fresh,
// secret, unbiased random nonce for every signature; a repeated or biased
// nonce leaks the private key (the PlayStation 3 and several Bitcoin
// wallet breaks). Its implementations are also hard to make constant-time.
// EdDSA (RFC 8032) derives the nonce deterministically from the key and
// the message, so the same key and message always give the same signature
// and no RNG is involved at signing time; Ed25519 is fast, has small keys
// (32 bytes) and signatures (64 bytes), and is designed for constant-time
// code. Java 15 adds it to the SunEC provider as "Ed25519" / "Ed448".
// This program checks the implementation against RFC 8032's first test
// vector, then shows that tampering is detected.
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.EdECPoint;
import java.security.spec.EdECPrivateKeySpec;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.NamedParameterSpec;

public class EdDsaSignatures {
    // RFC 8032, section 7.1, TEST 1 (empty message)
    static final String SECRET = "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60";
    static final String PUBLIC = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a";
    static final String EXPECTED_SIG = "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155"
            + "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b";

    public static void main(String[] args) throws Exception {
        // Before: ECDSA on P-256, randomized -> two signatures of the same data differ
        KeyPairGenerator ecGen = KeyPairGenerator.getInstance("EC");
        ecGen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair ec = ecGen.generateKeyPair();
        byte[] data = "transfer 100 to bob".getBytes(StandardCharsets.UTF_8);
        boolean ecdsaSame = hex(sign("SHA256withECDSA", ec.getPrivate(), data))
                .equals(hex(sign("SHA256withECDSA", ec.getPrivate(), data)));
        System.out.println("ECDSA: signing twice gives the same signature = " + ecdsaSame);

        // After: Ed25519, keys rebuilt from the RFC's raw bytes
        KeyFactory kf = KeyFactory.getInstance("Ed25519");
        PrivateKey priv = kf.generatePrivate(new EdECPrivateKeySpec(NamedParameterSpec.ED25519, unhex(SECRET)));
        PublicKey pub = kf.generatePublic(new EdECPublicKeySpec(NamedParameterSpec.ED25519, decodePoint(unhex(PUBLIC))));

        byte[] sig = sign("Ed25519", priv, new byte[0]);
        System.out.println("Ed25519: signature length = " + sig.length + " bytes");
        System.out.println("Ed25519: matches RFC 8032 test 1 = " + hex(sig).equals(EXPECTED_SIG));
        System.out.println("Ed25519: deterministic = " + hex(sign("Ed25519", priv, data)).equals(hex(sign("Ed25519", priv, data))));
        System.out.println("Ed25519: verifies = " + verify(pub, new byte[0], sig));

        // Any change to message or signature is detected
        byte[] signed = sign("Ed25519", priv, data);
        byte[] tamperedData = "transfer 900 to bob".getBytes(StandardCharsets.UTF_8);
        System.out.println("Ed25519: tampered message verifies = " + verify(pub, tamperedData, signed));
        signed[40] ^= 1; // flip one bit in the S half
        System.out.println("Ed25519: tampered signature verifies = " + verify(pub, data, signed));

        // A fresh key pair is one call away
        KeyPair fresh = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        System.out.println("fresh key algorithm = " + fresh.getPublic().getAlgorithm()
                + ", X.509 encoding = " + fresh.getPublic().getEncoded().length + " bytes");
    }

    static byte[] sign(String alg, PrivateKey key, byte[] data) throws Exception {
        Signature s = Signature.getInstance(alg);
        s.initSign(key);
        s.update(data);
        return s.sign();
    }

    static boolean verify(PublicKey key, byte[] data, byte[] sig) throws Exception {
        Signature s = Signature.getInstance("Ed25519");
        s.initVerify(key);
        s.update(data);
        return s.verify(sig);
    }

    // RFC 8032 point encoding: little-endian y, top bit of the last byte = x is odd
    static EdECPoint decodePoint(byte[] encoded) {
        byte[] be = new byte[encoded.length];
        for (int i = 0; i < encoded.length; i++) {
            be[i] = encoded[encoded.length - 1 - i];
        }
        boolean xOdd = (be[0] & 0x80) != 0;
        be[0] &= 0x7f;
        return new EdECPoint(xOdd, new BigInteger(1, be));
    }

    static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    static byte[] unhex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }
}

/* Output:
ECDSA: signing twice gives the same signature = false
Ed25519: signature length = 64 bytes
Ed25519: matches RFC 8032 test 1 = true
Ed25519: deterministic = true
Ed25519: verifies = true
Ed25519: tampered message verifies = false
Ed25519: tampered signature verifies = false
fresh key algorithm = EdDSA, X.509 encoding = 44 bytes
*/
