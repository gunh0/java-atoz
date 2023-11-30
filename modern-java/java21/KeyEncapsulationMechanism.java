// JEP 452 — Key Encapsulation Mechanism API (Java 21)
//
// To send a symmetric key to someone who only has your public key, the
// textbook approaches are RSA key wrapping (padding pitfalls, no forward
// secrecy) or a hand-assembled Diffie-Hellman exchange: generate an
// ephemeral key pair, run KeyAgreement, then hash the raw shared secret
// into a key yourself, with plenty of room for mistakes. A KEM packages
// this as two calls: the sender's encapsulate() returns a fresh secret key
// plus an "encapsulation" message, and the receiver's decapsulate() turns
// that message back into the same key with its private key. KEMs are the
// building block of HPKE (RFC 9180) and of post-quantum schemes such as
// ML-KEM. Java 21 adds javax.crypto.KEM with DHKEM (RFC 9180) over
// X25519, X448 and the NIST curves. Everything here stays in-process.
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KEM;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class KeyEncapsulationMechanism {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("X25519");
        KeyPair receiver = gen.generateKeyPair(); // the receiver publishes receiver.getPublic()

        // Before: ephemeral DH by hand, then derive a key from the raw secret yourself
        KeyPair ephemeral = gen.generateKeyPair();
        KeyAgreement senderAgreement = KeyAgreement.getInstance("X25519");
        senderAgreement.init(ephemeral.getPrivate());
        senderAgreement.doPhase(receiver.getPublic(), true);
        byte[] senderKey = MessageDigest.getInstance("SHA-256").digest(senderAgreement.generateSecret()); // ad-hoc KDF
        KeyAgreement receiverAgreement = KeyAgreement.getInstance("X25519");
        receiverAgreement.init(receiver.getPrivate());
        receiverAgreement.doPhase(ephemeral.getPublic(), true); // the sender must ship its public key
        byte[] receiverKey = MessageDigest.getInstance("SHA-256").digest(receiverAgreement.generateSecret());
        System.out.println("before: manual DH keys match = " + Arrays.equals(senderKey, receiverKey));

        // After: the KEM API
        KEM kem = KEM.getInstance("DHKEM");

        // Sender side: only needs the receiver's public key
        KEM.Encapsulator encapsulator = kem.newEncapsulator(receiver.getPublic());
        KEM.Encapsulated encapsulated = encapsulator.encapsulate(0, 32, "AES"); // a 256-bit AES key
        SecretKey sendKey = encapsulated.key();
        byte[] message = encapsulated.encapsulation(); // send this along with the ciphertext
        System.out.println("after: secret size " + encapsulator.secretSize() + " bytes, encapsulation "
                + message.length + " bytes, key algorithm " + sendKey.getAlgorithm());

        byte[] iv = new byte[12]; // a fresh key per message, so a fixed IV is acceptable here
        byte[] ciphertext = aes(Cipher.ENCRYPT_MODE, sendKey, iv,
                "wire 100 EUR to account 42".getBytes(StandardCharsets.UTF_8));

        // Receiver side: private key + encapsulation -> the same secret key
        KEM.Decapsulator decapsulator = kem.newDecapsulator(receiver.getPrivate());
        SecretKey recvKey = decapsulator.decapsulate(message, 0, 32, "AES");
        System.out.println("after: keys match = " + MessageDigest.isEqual(sendKey.getEncoded(), recvKey.getEncoded()));
        System.out.println("after: decrypted = "
                + new String(aes(Cipher.DECRYPT_MODE, recvKey, iv, ciphertext), StandardCharsets.UTF_8));

        // Every encapsulation produces a new random key
        KEM.Encapsulated again = encapsulator.encapsulate(0, 32, "AES");
        System.out.println("second encapsulation gives a different key = "
                + !Arrays.equals(again.key().getEncoded(), sendKey.getEncoded()));

        // Someone else's private key yields a different key, so decryption fails
        KeyPair stranger = gen.generateKeyPair();
        SecretKey wrong = kem.newDecapsulator(stranger.getPrivate()).decapsulate(message, 0, 32, "AES");
        try {
            aes(Cipher.DECRYPT_MODE, wrong, iv, ciphertext);
        } catch (AEADBadTagException e) {
            System.out.println("wrong private key: AEADBadTagException");
        }
    }

    static byte[] aes(int mode, SecretKey key, byte[] iv, byte[] input) throws Exception {
        Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
        gcm.init(mode, new SecretKeySpec(key.getEncoded(), "AES"), new GCMParameterSpec(128, iv));
        return gcm.doFinal(input);
    }
}

/* Output:
before: manual DH keys match = true
after: secret size 32 bytes, encapsulation 32 bytes, key algorithm AES
after: keys match = true
after: decrypted = wire 100 EUR to account 42
second encapsulation gives a different key = true
wrong private key: AEADBadTagException
*/
