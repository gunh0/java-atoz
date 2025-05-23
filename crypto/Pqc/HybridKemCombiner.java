// Hybrid key exchange: X25519 + ML-KEM-768 with an X-Wing-style combiner
//
// ML-KEM is new; X25519 has decades of elliptic-curve cryptanalysis behind
// it but falls to a quantum computer. Hybrids run both and derive one key
// from both shared secrets, so an attacker must break BOTH: a classical
// attacker who finds a flaw in ML-KEM still faces X25519, and a future
// quantum attacker still faces ML-KEM. TLS does this as the
// X25519MLKEM768 group (the two secrets are concatenated into the TLS 1.3
// key schedule, whose HKDF does the combining); X-Wing (an IETF draft) is a
// stand-alone KEM that hashes
//     SHA3-256(ss_M || ss_X || ct_X || pk_X || label)
// binding in the X25519 ciphertext and public key, so the classical half
// cannot be swapped for another one. This file builds that shape from JDK
// parts (KEM "ML-KEM", KeyAgreement "X25519", MessageDigest "SHA3-256");
// it is not byte-compatible with X-Wing (which derives keys from seeds), so
// use it to understand the construction, and a vetted implementation to
// ship. Then it checks: both sides agree; one broken half is not enough;
// mixing parts of two exchanges fails.
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.XECPublicKey;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPublicKeySpec;
import javax.crypto.KEM;
import javax.crypto.KeyAgreement;

public class HybridKemCombiner {
    static final byte[] LABEL = {0x5c, 0x2e, 0x2f, 0x2f, 0x5e, 0x5c}; // "\.//^\", X-Wing's domain separator

    record RecipientKeys(KeyPair mlkem, KeyPair x25519) {}
    record Encapsulation(byte[] ctM, byte[] ctX) {
        byte[] wire() { return ByteBuffer.allocate(ctM.length + ctX.length).put(ctM).put(ctX).array(); }
    }
    record Result(byte[] key, Encapsulation enc, byte[] ssM, byte[] ssX) {}

    static byte[] raw(PublicKey xec) { // X25519 public key as 32 little-endian bytes (RFC 7748)
        byte[] be = ((XECPublicKey) xec).getU().toByteArray();
        byte[] le = new byte[32];
        for (int i = 0; i < 32 && i < be.length; i++) le[i] = be[be.length - 1 - i];
        return le;
    }

    static PublicKey x25519FromRaw(byte[] le) throws Exception {
        byte[] be = new byte[32];
        for (int i = 0; i < 32; i++) be[i] = le[31 - i];
        return KeyFactory.getInstance("X25519").generatePublic(new XECPublicKeySpec(NamedParameterSpec.X25519, new BigInteger(1, be)));
    }

    static byte[] x25519(PrivateKey priv, PublicKey pub) throws Exception {
        KeyAgreement ka = KeyAgreement.getInstance("X25519");
        ka.init(priv);
        ka.doPhase(pub, true);
        return ka.generateSecret();
    }

    static byte[] combine(byte[] ssM, byte[] ssX, byte[] ctX, byte[] pkX) throws Exception {
        MessageDigest sha3 = MessageDigest.getInstance("SHA3-256");
        sha3.update(ssM);
        sha3.update(ssX);
        sha3.update(ctX);
        sha3.update(pkX);
        return sha3.digest(LABEL);
    }

    static Result encapsulate(PublicKey pkM, PublicKey pkX) throws Exception {
        KEM.Encapsulated m = KEM.getInstance("ML-KEM").newEncapsulator(pkM).encapsulate();
        KeyPair eph = KeyPairGenerator.getInstance("X25519").generateKeyPair(); // the X25519 "ciphertext" is an ephemeral public key
        byte[] ssX = x25519(eph.getPrivate(), pkX);
        byte[] ctX = raw(eph.getPublic());
        byte[] ssM = m.key().getEncoded();
        return new Result(combine(ssM, ssX, ctX, raw(pkX)), new Encapsulation(m.encapsulation(), ctX), ssM, ssX);
    }

    static byte[] decapsulate(RecipientKeys keys, Encapsulation enc) throws Exception {
        byte[] ssM = KEM.getInstance("ML-KEM").newDecapsulator(keys.mlkem().getPrivate()).decapsulate(enc.ctM()).getEncoded();
        byte[] ssX = x25519(keys.x25519().getPrivate(), x25519FromRaw(enc.ctX()));
        return combine(ssM, ssX, enc.ctX(), raw(keys.x25519().getPublic()));
    }

    public static void main(String[] args) throws Exception {
        RecipientKeys bob = new RecipientKeys(KeyPairGenerator.getInstance("ML-KEM-768").generateKeyPair(),
                KeyPairGenerator.getInstance("X25519").generateKeyPair());

        Result alice = encapsulate(bob.mlkem().getPublic(), bob.x25519().getPublic());
        byte[] bobKey = decapsulate(bob, alice.enc());
        System.out.println("wire size: " + alice.enc().ctM().length + " (ML-KEM) + " + alice.enc().ctX().length
                + " (X25519) = " + alice.enc().wire().length + " bytes");
        System.out.println("combined key bytes: " + alice.key().length + ", both sides agree: " + MessageDigest.isEqual(alice.key(), bobKey));

        // Attacker who broke ONE half learns that half's secret, but not the other
        byte[] guess = new byte[32];
        byte[] quantumAttacker = combine(guess, alice.ssX(), alice.enc().ctX(), raw(bob.x25519().getPublic()));
        byte[] latticeAttacker = combine(alice.ssM(), guess, alice.enc().ctX(), raw(bob.x25519().getPublic()));
        System.out.println("knows ss_X only (X25519 broken), key recovered: " + MessageDigest.isEqual(quantumAttacker, alice.key()));
        System.out.println("knows ss_M only (ML-KEM broken), key recovered: " + MessageDigest.isEqual(latticeAttacker, alice.key()));

        // Mix and match: ML-KEM part of one exchange with X25519 part of another
        Result other = encapsulate(bob.mlkem().getPublic(), bob.x25519().getPublic());
        byte[] mixed = decapsulate(bob, new Encapsulation(alice.enc().ctM(), other.enc().ctX()));
        System.out.println("spliced encapsulation gives either key: "
                + (MessageDigest.isEqual(mixed, alice.key()) || MessageDigest.isEqual(mixed, other.key())));
    }
}

/* Output:
wire size: 1088 (ML-KEM) + 32 (X25519) = 1120 bytes
combined key bytes: 32, both sides agree: true
knows ss_X only (X25519 broken), key recovered: false
knows ss_M only (ML-KEM broken), key recovered: false
spliced encapsulation gives either key: false
*/
