// JEP 496 — Quantum-Resistant Module-Lattice-Based Key Encapsulation Mechanism (Java 24)
//
// Key exchange in TLS, SSH and HPKE rests on (elliptic-curve) Diffie-Hellman,
// which Shor's algorithm breaks; traffic recorded today could be decrypted
// once large quantum computers exist. NIST's answer for key establishment is
// ML-KEM (FIPS 203, 2024). Java 21 had already added the KEM API
// (javax.crypto.KEM, java21/KeyEncapsulationMechanism.java) with DHKEM only;
// Java 24 plugs ML-KEM into the same API and into KeyPairGenerator and
// KeyFactory, with ML-KEM-512, ML-KEM-768 (the default) and ML-KEM-1024 and
// constants such as NamedParameterSpec.ML_KEM_768. So migrating is a change
// of algorithm names; the visible cost is size: kilobyte keys and
// ciphertexts instead of 32 bytes. The deeper treatment (implicit rejection,
// key derivation, hybrids with X25519) is in crypto/Pqc/.
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.spec.NamedParameterSpec;
import javax.crypto.KEM;

public class QuantumResistantKem {
    // One routine for any KEM: only the names differ
    static void exchange(String keyAlg, NamedParameterSpec params, String kemAlg) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(keyAlg);
        if (params != null) kpg.initialize(params);
        KeyPair receiver = kpg.generateKeyPair();

        KEM kem = KEM.getInstance(kemAlg);
        KEM.Encapsulated sent = kem.newEncapsulator(receiver.getPublic()).encapsulate();
        byte[] received = kem.newDecapsulator(receiver.getPrivate()).decapsulate(sent.encapsulation()).getEncoded();

        System.out.printf("%-12s public key %4d B, encapsulation %4d B, secret %d B, agree: %s%n",
                params == null ? keyAlg : params.getName(), receiver.getPublic().getEncoded().length,
                sent.encapsulation().length, received.length, MessageDigest.isEqual(received, sent.key().getEncoded()));
    }

    public static void main(String[] args) throws Exception {
        // Before (Java 21): DHKEM over X25519, classical security only
        exchange("X25519", null, "DHKEM");

        // After (Java 24): the same calls with ML-KEM
        exchange("ML-KEM", NamedParameterSpec.ML_KEM_512, "ML-KEM");
        exchange("ML-KEM", NamedParameterSpec.ML_KEM_768, "ML-KEM");
        exchange("ML-KEM", NamedParameterSpec.ML_KEM_1024, "ML-KEM");

        // The default parameter set, and the provider that implements it
        KeyPair kp = KeyPairGenerator.getInstance("ML-KEM").generateKeyPair();
        KEM.Encapsulator enc = KEM.getInstance("ML-KEM").newEncapsulator(kp.getPublic());
        System.out.println("default KeyPairGenerator(\"ML-KEM\"): encapsulation " + enc.encapsulationSize()
                + " B (ML-KEM-768), provider " + enc.providerName());
    }
}

/* Output:
X25519       public key   44 B, encapsulation   32 B, secret 32 B, agree: true
ML-KEM-512   public key  822 B, encapsulation  768 B, secret 32 B, agree: true
ML-KEM-768   public key 1206 B, encapsulation 1088 B, secret 32 B, agree: true
ML-KEM-1024  public key 1590 B, encapsulation 1568 B, secret 32 B, agree: true
default KeyPairGenerator("ML-KEM"): encapsulation 1088 B (ML-KEM-768), provider SunJCE
*/
