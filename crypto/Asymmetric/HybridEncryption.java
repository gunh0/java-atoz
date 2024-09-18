// Hybrid public-key encryption, ECIES-style: X25519 + HKDF + AES-GCM
//
// Public-key primitives cannot encrypt bulk data, so real systems combine
// them: the sender generates an EPHEMERAL key pair, does Diffie-Hellman with
// the recipient's static public key, derives a one-time AES key with HKDF,
// encrypts with AES-GCM, and sends ephemeral public key || ciphertext. Only
// the recipient's private key can redo the DH. This is the shape of ECIES,
// of HPKE (RFC 9180) and of the KEM API added in Java 21
// (modern-java/java21/KeyEncapsulationMechanism.java), which packages the
// first half. Details that matter, all visible below: bind the ephemeral and
// recipient public keys into the KDF (so the ciphertext cannot be re-targeted),
// use a fresh ephemeral key per message (which also makes a fixed GCM nonce
// safe, since every key is used once), and authenticate everything.
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.XECPublicKey;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPublicKeySpec;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class HybridEncryption {
    static final byte[] INFO = "java-atoz hybrid v1: X25519, HKDF-SHA256, AES-256-GCM".getBytes(StandardCharsets.US_ASCII);

    // Output: ephemeral public key (32 bytes, little-endian u) || ciphertext || tag
    static byte[] encrypt(PublicKey recipient, byte[] plaintext, byte[] aad) throws Exception {
        KeyPair eph = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        byte[] ephBytes = encode((XECPublicKey) eph.getPublic());
        byte[] key = deriveKey(eph.getPrivate(), recipient, ephBytes, encode((XECPublicKey) recipient));
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, new byte[12]));
        c.updateAAD(aad);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(ephBytes);
        out.writeBytes(c.doFinal(plaintext));
        return out.toByteArray();
    }

    static byte[] decrypt(KeyPair recipient, byte[] message, byte[] aad) throws Exception {
        byte[] ephBytes = Arrays.copyOf(message, 32);
        PublicKey eph = decode(ephBytes);
        byte[] key = deriveKey(recipient.getPrivate(), eph, ephBytes, encode((XECPublicKey) recipient.getPublic()));
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, new byte[12]));
        c.updateAAD(aad);
        return c.doFinal(message, 32, message.length - 32);
    }

    // HKDF-SHA256: salt = eph || recipient public key, info = suite label
    static byte[] deriveKey(PrivateKey mine, PublicKey theirs, byte[] ephPub, byte[] recipientPub) throws Exception {
        KeyAgreement ka = KeyAgreement.getInstance("X25519");
        ka.init(mine);
        ka.doPhase(theirs, true);
        byte[] shared = ka.generateSecret();
        Mac mac = Mac.getInstance("HmacSHA256");
        byte[] salt = new byte[64];
        System.arraycopy(ephPub, 0, salt, 0, 32);
        System.arraycopy(recipientPub, 0, salt, 32, 32);
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(shared);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(INFO);
        return mac.doFinal(new byte[] {1});
    }

    static byte[] encode(XECPublicKey key) {
        byte[] be = key.getU().toByteArray();
        byte[] le = new byte[32];
        for (int i = 0; i < 32 && i < be.length; i++) le[i] = be[be.length - 1 - i];
        return le;
    }

    static PublicKey decode(byte[] le) throws Exception {
        byte[] be = new byte[32];
        for (int i = 0; i < 32; i++) be[i] = le[31 - i];
        return KeyFactory.getInstance("XDH").generatePublic(
                new XECPublicKeySpec(NamedParameterSpec.X25519, new BigInteger(1, be)));
    }

    public static void main(String[] args) throws Exception {
        KeyPair bob = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        KeyPair carol = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        byte[] aad = "to=bob".getBytes(StandardCharsets.US_ASCII);
        byte[] msg = "the launch code is 0000".getBytes(StandardCharsets.US_ASCII);

        byte[] m1 = encrypt(bob.getPublic(), msg, aad);
        byte[] m2 = encrypt(bob.getPublic(), msg, aad);
        System.out.println("message " + msg.length + " bytes -> " + m1.length + " bytes (32 ephemeral key + "
                + msg.length + " ciphertext + 16 tag)");
        System.out.println("two encryptions differ (fresh ephemeral key): " + !Arrays.equals(m1, m2));
        System.out.println("bob decrypts: " + new String(decrypt(bob, m1, aad), StandardCharsets.US_ASCII));

        System.out.println("carol decrypts: " + attempt(carol, m1, aad));
        byte[] tampered = m1.clone();
        tampered[40] ^= 1;
        System.out.println("tampered ciphertext: " + attempt(bob, tampered, aad));
        System.out.println("different AAD: " + attempt(bob, m1, "to=eve".getBytes(StandardCharsets.US_ASCII)));
        // Swapping in another ephemeral key changes the derived key: rejected too
        byte[] swapped = m1.clone();
        System.arraycopy(m2, 0, swapped, 0, 32);
        System.out.println("ephemeral key swapped: " + attempt(bob, swapped, aad));
    }

    static String attempt(KeyPair who, byte[] m, byte[] aad) throws Exception {
        try {
            return new String(decrypt(who, m, aad), StandardCharsets.US_ASCII);
        } catch (AEADBadTagException e) {
            return "rejected (" + e.getClass().getSimpleName() + ")";
        }
    }
}

/* Output:
message 23 bytes -> 71 bytes (32 ephemeral key + 23 ciphertext + 16 tag)
two encryptions differ (fresh ephemeral key): true
bob decrypts: the launch code is 0000
carol decrypts: rejected (AEADBadTagException)
tampered ciphertext: rejected (AEADBadTagException)
different AAD: rejected (AEADBadTagException)
ephemeral key swapped: rejected (AEADBadTagException)
*/
