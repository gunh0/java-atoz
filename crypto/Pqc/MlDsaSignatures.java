// ML-DSA (FIPS 204) signatures: sizes, verification, tamper detection
//
// ML-DSA (formerly CRYSTALS-Dilithium), standardised as FIPS 204 in August
// 2024, is the lattice-based replacement for RSA and ECDSA signatures once
// quantum computers make those forgeable. Java 24 provides it in the SUN
// provider: KeyPairGenerator "ML-DSA" (default ML-DSA-65) or "ML-DSA-44" /
// "-65" / "-87" (NIST security categories 2, 3 and 5), and Signature
// "ML-DSA" (pure mode: the whole message is signed, no separate pre-hash).
// The price is size: an ML-DSA-65 signature is 3,309 bytes and its public key
// 1,952 bytes, against about 72 and 91 for ECDSA P-256, which matters for
// certificate chains, TLS handshakes and tokens. Signing is "hedged" by
// default: fresh randomness is mixed in, so two signatures of the same
// message differ, yet both verify. Unlike ECDSA, a bad nonce source does not
// leak the key. Verification must still be strict: any change to the
// message, the signature or the key makes it fail.
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.List;

public class MlDsaSignatures {
    static byte[] sign(String alg, PrivateKey key, byte[] msg) throws Exception {
        Signature s = Signature.getInstance(alg);
        s.initSign(key);
        s.update(msg);
        return s.sign();
    }

    // false for a well-formed but wrong signature; a malformed one throws SignatureException
    static String verify(String alg, PublicKey key, byte[] msg, byte[] sig) throws Exception {
        Signature s = Signature.getInstance(alg);
        s.initVerify(key);
        s.update(msg);
        try {
            return String.valueOf(s.verify(sig));
        } catch (SignatureException e) {
            return "SignatureException (malformed)";
        }
    }

    public static void main(String[] args) throws Exception {
        byte[] msg = "release-2025.04.tar.gz sha256=9f86d081884c7d65".getBytes(StandardCharsets.UTF_8);

        System.out.println("algorithm     public key (X.509)  signature");
        for (String ps : List.of("ML-DSA-44", "ML-DSA-65", "ML-DSA-87")) {
            KeyPair kp = KeyPairGenerator.getInstance(ps).generateKeyPair();
            System.out.printf("%-13s %18d %10d%n", ps, kp.getPublic().getEncoded().length, sign("ML-DSA", kp.getPrivate(), msg).length);
        }
        KeyPairGenerator ec = KeyPairGenerator.getInstance("EC");
        ec.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair ecKp = ec.generateKeyPair();
        System.out.printf("%-13s %18d %10s%n", "ECDSA P-256", ecKp.getPublic().getEncoded().length, "70-72");

        KeyPair kp = KeyPairGenerator.getInstance("ML-DSA").generateKeyPair(); // ML-DSA-65
        Signature probe = Signature.getInstance("ML-DSA");
        probe.initSign(kp.getPrivate());
        System.out.println("default key: " + kp.getPublic().getAlgorithm() + ", provider: " + probe.getProvider().getName());

        byte[] sig = sign("ML-DSA", kp.getPrivate(), msg);
        byte[] sig2 = sign("ML-DSA", kp.getPrivate(), msg);
        System.out.println("verify:                       " + verify("ML-DSA", kp.getPublic(), msg, sig));
        System.out.println("two signatures identical:     " + Arrays.equals(sig, sig2) + " (hedged signing)");
        System.out.println("second signature verifies:    " + verify("ML-DSA", kp.getPublic(), msg, sig2));

        byte[] otherMsg = msg.clone();
        otherMsg[otherMsg.length - 1] ^= 1;
        System.out.println("one message bit flipped:      " + verify("ML-DSA", kp.getPublic(), otherMsg, sig));
        byte[] badSig = sig.clone();
        badSig[1000] ^= 1;
        System.out.println("one signature bit flipped:    " + verify("ML-DSA", kp.getPublic(), msg, badSig));
        System.out.println("truncated signature:          " + verify("ML-DSA", kp.getPublic(), msg, Arrays.copyOf(sig, sig.length - 1)));
        KeyPair other = KeyPairGenerator.getInstance("ML-DSA").generateKeyPair();
        System.out.println("someone else's public key:    " + verify("ML-DSA", other.getPublic(), msg, sig));

        // A parameter-set-specific Signature refuses keys of another set
        try {
            Signature.getInstance("ML-DSA-44").initVerify(kp.getPublic());
            System.out.println("ML-DSA-44 accepted an ML-DSA-65 key?!");
        } catch (InvalidKeyException e) {
            System.out.println("ML-DSA-44 verifier, ML-DSA-65 key: InvalidKeyException");
        }
    }
}

/* Output:
algorithm     public key (X.509)  signature
ML-DSA-44                   1334       2420
ML-DSA-65                   1974       3309
ML-DSA-87                   2614       4627
ECDSA P-256                   91      70-72
default key: ML-DSA, provider: SUN
verify:                       true
two signatures identical:     false (hedged signing)
second signature verifies:    true
one message bit flipped:      false
one signature bit flipped:    false
truncated signature:          SignatureException (malformed)
someone else's public key:    false
ML-DSA-44 verifier, ML-DSA-65 key: InvalidKeyException
*/
