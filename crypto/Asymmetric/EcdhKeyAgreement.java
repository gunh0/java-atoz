// ECDH key agreement on P-256, followed by a KDF
//
// In (elliptic-curve) Diffie-Hellman each side combines its own private key
// with the other's public key and both arrive at the same shared secret,
// which never crosses the wire. Three rules: (1) the raw secret (the
// x-coordinate of a point) is not a key: run it through a KDF such as HKDF,
// binding in context such as both public keys and a protocol label;
// (2) ECDH alone is unauthenticated, so a man in the middle can run one
// exchange with each side; sign the ephemeral public keys or check them
// against a certificate; (3) validate the peer's public key (on the right
// curve, on the curve at all), or invalid-curve attacks can leak your
// private key; the JDK's KeyAgreement does this check. Fresh (ephemeral) key
// pairs per session give forward secrecy.
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class EcdhKeyAgreement {
    public static void main(String[] args) throws Exception {
        KeyPair alice = generate("secp256r1");
        KeyPair bob = generate("secp256r1");

        byte[] zA = agree(alice, bob.getPublic());
        byte[] zB = agree(bob, alice.getPublic());
        System.out.println("shared secret: " + zA.length + " bytes, both sides equal: " + MessageDigest.isEqual(zA, zB));

        // Turn the raw secret into keys: HKDF(salt = transcript hash, info = purpose)
        MessageDigest th = MessageDigest.getInstance("SHA-256");
        th.update(alice.getPublic().getEncoded());
        th.update(bob.getPublic().getEncoded());
        byte[] transcript = th.digest();
        byte[] keyA = hkdf(transcript, zA, "demo-v1 aes-256-gcm key");
        byte[] keyB = hkdf(transcript, zB, "demo-v1 aes-256-gcm key");
        System.out.println("derived 256-bit keys equal: " + MessageDigest.isEqual(keyA, keyB));

        // A third party with its own key pair gets a different secret
        KeyPair eve = generate("secp256r1");
        System.out.println("eve's secret with bob equals alice's: " + MessageDigest.isEqual(agree(eve, bob.getPublic()), zA));

        // A public key from another curve is rejected, not silently combined
        KeyPair p384 = generate("secp384r1");
        try {
            agree(alice, p384.getPublic());
            System.out.println("P-384 key accepted?!");
        } catch (InvalidKeyException e) {
            System.out.println("peer key on another curve: " + e.getClass().getSimpleName());
        }
    }

    static KeyPair generate(String curve) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec(curve));
        return kpg.generateKeyPair();
    }

    static byte[] agree(KeyPair mine, PublicKey theirs) throws Exception {
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(mine.getPrivate());
        ka.doPhase(theirs, true);
        return ka.generateSecret();
    }

    // HKDF-SHA256 with a single output block (RFC 5869)
    static byte[] hkdf(byte[] salt, byte[] ikm, String info) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(info.getBytes(StandardCharsets.US_ASCII));
        return mac.doFinal(new byte[] {1});
    }
}

/* Output:
shared secret: 32 bytes, both sides equal: true
derived 256-bit keys equal: true
eve's secret with bob equals alice's: false
peer key on another curve: InvalidKeyException
*/
