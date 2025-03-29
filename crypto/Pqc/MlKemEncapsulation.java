// ML-KEM (FIPS 203) through the KEM API: sizes, implicit rejection, use
//
// A large enough quantum computer running Shor's algorithm breaks RSA and
// every elliptic-curve scheme, including the X25519 and ECDH key exchanges
// that protect today's traffic, and an attacker can record that traffic now
// and decrypt it later ("harvest now, decrypt later"). ML-KEM, standardised
// by NIST in August 2024 as FIPS 203 (it was CRYSTALS-Kyber), is a
// lattice-based key encapsulation mechanism believed to resist that. Java 24
// ships it in SunJCE behind the KEM API from Java 21: KeyPairGenerator
// "ML-KEM" (or "ML-KEM-512"/"-768"/"-1024"; 768 is the default and the usual
// choice, roughly AES-192 strength), KEM.getInstance("ML-KEM"),
// encapsulate() / decapsulate(). Things to know from a reviewer's seat:
// the keys and ciphertexts are big (about a kilobyte, versus 32 bytes for
// X25519); decapsulating a corrupted ciphertext does NOT throw, it returns a
// different, pseudo-random key ("implicit rejection", which defeats chosen-
// ciphertext probing), so the key must feed an AEAD whose tag catches it; and
// the 32-byte shared secret should go through a KDF with context before use.
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.util.List;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KEM;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MlKemEncapsulation {
    public static void main(String[] args) throws Exception {
        System.out.println("parameter set  public key (X.509)  encapsulation  shared secret");
        for (NamedParameterSpec ps : List.of(NamedParameterSpec.ML_KEM_512, NamedParameterSpec.ML_KEM_768, NamedParameterSpec.ML_KEM_1024)) {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("ML-KEM");
            kpg.initialize(ps);
            KeyPair kp = kpg.generateKeyPair();
            KEM.Encapsulator enc = KEM.getInstance("ML-KEM").newEncapsulator(kp.getPublic());
            System.out.printf("%-14s %18d %14d %14d%n", ps.getName(), kp.getPublic().getEncoded().length,
                    enc.encapsulationSize(), enc.secretSize());
        }

        // Receiver: a long-term ML-KEM-768 key pair; sender: only the public key
        KeyPair receiver = KeyPairGenerator.getInstance("ML-KEM-768").generateKeyPair();
        KEM kem = KEM.getInstance("ML-KEM");
        KEM.Encapsulated sent = kem.newEncapsulator(receiver.getPublic()).encapsulate();
        KEM.Decapsulator dec = kem.newDecapsulator(receiver.getPrivate());
        SecretKey got = dec.decapsulate(sent.encapsulation());
        System.out.println("provider: " + dec.providerName() + ", shared secret algorithm: " + sent.key().getAlgorithm());
        System.out.println("both sides agree: " + MessageDigest.isEqual(sent.key().getEncoded(), got.getEncoded()));

        // Encapsulation is randomized: a second run gives a new secret and ciphertext
        KEM.Encapsulated again = kem.newEncapsulator(receiver.getPublic()).encapsulate();
        System.out.println("second encapsulation, same secret: " + MessageDigest.isEqual(sent.key().getEncoded(), again.key().getEncoded()));

        // Implicit rejection: a flipped bit yields a wrong key, not an exception
        byte[] corrupted = sent.encapsulation().clone();
        corrupted[100] ^= 0x01;
        SecretKey wrong = dec.decapsulate(corrupted);
        System.out.println("corrupted ciphertext: decapsulate returned a key; it matches: "
                + MessageDigest.isEqual(sent.key().getEncoded(), wrong.getEncoded()));

        // Derive the AEAD key with context (HKDF-style extract+expand over HMAC-SHA256)
        byte[] info = "example-app v1 aes-256-gcm".getBytes(StandardCharsets.UTF_8);
        SecretKey senderAes = derive(sent.key(), sent.encapsulation(), info);
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, senderAes, new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal("quantum-safe hello".getBytes(StandardCharsets.UTF_8));

        System.out.println("receiver decrypts: " + open(derive(got, sent.encapsulation(), info), iv, ct));
        System.out.println("receiver with the implicitly rejected key: " + open(derive(wrong, corrupted, info), iv, ct));

        // A KEM can also hand out a slice of the secret as a typed key
        SecretKey aes128 = kem.newEncapsulator(receiver.getPublic()).encapsulate(0, 16, "AES").key();
        System.out.println("encapsulate(0, 16, \"AES\"): " + aes128.getAlgorithm() + " " + aes128.getEncoded().length * 8 + " bits");
    }

    static SecretKey derive(SecretKey shared, byte[] encapsulation, byte[] info) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(encapsulation, 0, 32, "HmacSHA256")); // salt: bind to this exchange
        byte[] prk = mac.doFinal(shared.getEncoded());
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(info);
        return new SecretKeySpec(mac.doFinal(new byte[] {1}), "AES");
    }

    static String open(SecretKey key, byte[] iv, byte[] ct) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        try {
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            return "AEADBadTagException (the tag catches it)";
        }
    }
}

/* Output:
parameter set  public key (X.509)  encapsulation  shared secret
ML-KEM-512                    822            768             32
ML-KEM-768                   1206           1088             32
ML-KEM-1024                  1590           1568             32
provider: SunJCE, shared secret algorithm: Generic
both sides agree: true
second encapsulation, same secret: false
corrupted ciphertext: decapsulate returned a key; it matches: false
receiver decrypts: quantum-safe hello
receiver with the implicitly rejected key: AEADBadTagException (the tag catches it)
encapsulate(0, 16, "AES"): AES 128 bits
*/
