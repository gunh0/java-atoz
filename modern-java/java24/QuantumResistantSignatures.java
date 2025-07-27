// JEP 497 — Quantum-Resistant Module-Lattice-Based Digital Signature Algorithm (Java 24)
//
// RSA, ECDSA and EdDSA signatures (java15/EdDsaSignatures.java) all become
// forgeable once a large quantum computer can run Shor's algorithm, and
// signatures are long-lived: firmware, certificates and signed documents
// must stay trustworthy for years. NIST standardised ML-DSA (FIPS 204, 2024)
// as the general-purpose replacement. Java 24 implements it in the SUN
// provider behind the existing APIs: KeyPairGenerator "ML-DSA" (ML-DSA-44,
// -65 by default, -87), KeyFactory "ML-DSA" and Signature "ML-DSA", with
// constants such as NamedParameterSpec.ML_DSA_65. Code written against
// Signature therefore migrates by changing names; what changes in practice is
// size (signatures of 2.4 to 4.6 KB instead of 64 bytes for Ed25519) and the
// need for protocols, certificates and storage to carry them. More detail,
// including tamper tests and key encodings, is in crypto/Pqc/.
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.NamedParameterSpec;

public class QuantumResistantSignatures {
    // One routine for any signature scheme: only the names differ
    static void signAndVerify(String keyAlg, NamedParameterSpec params, String sigAlg) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(keyAlg);
        if (params != null) kpg.initialize(params);
        KeyPair kp = kpg.generateKeyPair();
        byte[] message = "firmware-v2.bin sha256=4e1243bd22c66e76".getBytes(StandardCharsets.UTF_8);

        Signature signer = Signature.getInstance(sigAlg);
        signer.initSign(kp.getPrivate());
        signer.update(message);
        byte[] sig = signer.sign();

        Signature verifier = Signature.getInstance(sigAlg);
        verifier.initVerify(kp.getPublic());
        verifier.update(message);
        boolean ok = verifier.verify(sig);
        message[0] ^= 1;
        verifier.update(message);
        boolean tampered = verifier.verify(sig);

        System.out.printf("%-9s public key %4d B, signature %4d B, verifies: %s, after tampering: %s%n",
                params == null ? keyAlg : params.getName(), kp.getPublic().getEncoded().length, sig.length, ok, tampered);
    }

    public static void main(String[] args) throws Exception {
        // Before: Ed25519 (Java 15), classical security only
        signAndVerify("Ed25519", null, "Ed25519");

        // After (Java 24): the same calls with ML-DSA
        signAndVerify("ML-DSA", NamedParameterSpec.ML_DSA_44, "ML-DSA");
        signAndVerify("ML-DSA", NamedParameterSpec.ML_DSA_65, "ML-DSA");
        signAndVerify("ML-DSA", NamedParameterSpec.ML_DSA_87, "ML-DSA");

        Signature s = Signature.getInstance("ML-DSA");
        s.initSign(KeyPairGenerator.getInstance("ML-DSA").generateKeyPair().getPrivate());
        System.out.println("Signature(\"ML-DSA\") provider: " + s.getProvider().getName());
    }
}

/* Output:
Ed25519   public key   44 B, signature   64 B, verifies: true, after tampering: false
ML-DSA-44 public key 1334 B, signature 2420 B, verifies: true, after tampering: false
ML-DSA-65 public key 1974 B, signature 3309 B, verifies: true, after tampering: false
ML-DSA-87 public key 2614 B, signature 4627 B, verifies: true, after tampering: false
Signature("ML-DSA") provider: SUN
*/
