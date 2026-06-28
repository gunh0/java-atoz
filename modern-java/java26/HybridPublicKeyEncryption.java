// Hybrid Public Key Encryption: Cipher "HPKE" and HPKEParameterSpec (Java 26)
//
// Found by comparing the public APIs of Java 25 and 26. HPKE (RFC 9180) is
// the standard way to encrypt to someone's public key: a KEM (here
// DHKEM(X25519, HKDF-SHA256)) produces a shared secret plus an
// "encapsulation" to send along, a KDF turns it into an AEAD key and base
// nonce, and the AEAD (AES-GCM or ChaCha20-Poly1305) encrypts messages, each
// with its own sequence number. It is what MLS, TLS Encrypted Client Hello
// and Oblivious HTTP use. Before Java 26 you assembled it yourself, as in
// crypto/Asymmetric/HybridEncryption.java, or used the Java 21 KEM API and
// did the rest by hand. Now it is a Cipher: the sender inits with the
// recipient's public key and gets the encapsulation from getIV(); the
// recipient inits with its private key and that encapsulation. The `info`
// parameter binds the context (protocol, version), the AAD binds each
// message's metadata, and the auth mode also proves which sender key was used.
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPrivateKeySpec;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.HPKEParameterSpec;

public class HybridPublicKeyEncryption {
    static final HexFormat HEX = HexFormat.of();

    // DHKEM(X25519, HKDF-SHA256) + HKDF-SHA256 + AES-128-GCM: RFC 9180 suite 0x0020/0x0001/0x0001
    static HPKEParameterSpec suite() {
        return HPKEParameterSpec.of(HPKEParameterSpec.KEM_DHKEM_X25519_HKDF_SHA256,
                HPKEParameterSpec.KDF_HKDF_SHA256, HPKEParameterSpec.AEAD_AES_128_GCM);
    }

    public static void main(String[] args) throws Exception {
        // 1. RFC 9180 A.1 (base mode): the recipient side is deterministic, so the
        //    published ciphertexts must decrypt with the published private key
        PrivateKey skR = KeyFactory.getInstance("X25519").generatePrivate(new XECPrivateKeySpec(NamedParameterSpec.X25519,
                HEX.parseHex("4612c550263fc8ad58375df3f557aac531d26850903e55a9f23f21d8534e8ac8")));
        Cipher rfc = Cipher.getInstance("HPKE");
        rfc.init(Cipher.DECRYPT_MODE, skR, suite()
                .withInfo(HEX.parseHex("4f6465206f6e2061204772656369616e2055726e")) // "Ode on a Grecian Urn"
                .withEncapsulation(HEX.parseHex("37fda3567bdbd628e88668c3c8d7e97d1d1253b6d4ea6d44c150f741f1bf4431")));
        rfc.updateAAD(utf8("Count-0"));
        System.out.println("RFC 9180 A.1 seq 0: " + str(rfc.doFinal(HEX.parseHex(
                "f938558b5d72f1a23810b4be2ab4f84331acc02fc97babc53a52ae8218a355a96d8770ac83d07bea87e13c512a"))));
        rfc.updateAAD(utf8("Count-1"));
        System.out.println("RFC 9180 A.1 seq 1: " + str(rfc.doFinal(HEX.parseHex(
                "af2d7e9ac9ae7e270f46ba1f975be53c09f8d875bdc8535458c2494e8a6eab251c03d0c22a56b8ca42c2063b84"))));

        // 2. Sender and recipient
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");
        KeyPair recipient = kpg.generateKeyPair();
        byte[] info = utf8("example-app v1 file encryption");

        Cipher sender = Cipher.getInstance("HPKE");
        sender.init(Cipher.ENCRYPT_MODE, recipient.getPublic(), suite().withInfo(info));
        byte[] enc = sender.getIV(); // the encapsulation: send it with the ciphertexts
        sender.updateAAD(utf8("part 1/2"));
        byte[] c1 = sender.doFinal(utf8("attack at dawn"));
        sender.updateAAD(utf8("part 2/2"));
        byte[] c2 = sender.doFinal(utf8("attack at dawn"));
        System.out.println("encapsulation " + enc.length + " bytes, ciphertext " + c1.length
                + " bytes (14 + 16-byte tag), same plaintext twice gives different ciphertexts: " + !HEX.formatHex(c1).equals(HEX.formatHex(c2)));

        Cipher receiver = Cipher.getInstance("HPKE");
        receiver.init(Cipher.DECRYPT_MODE, recipient.getPrivate(), suite().withInfo(info).withEncapsulation(enc));
        receiver.updateAAD(utf8("part 1/2"));
        String p1 = str(receiver.doFinal(c1));
        receiver.updateAAD(utf8("part 2/2"));
        System.out.println("decrypted in order: " + p1 + " / " + str(receiver.doFinal(c2)));

        // 3. Everything is bound: context, metadata, order
        System.out.println("wrong info:  " + attempt(recipient.getPrivate(), suite().withInfo(utf8("other-app v1")).withEncapsulation(enc), "part 1/2", c1));
        System.out.println("wrong AAD:   " + attempt(recipient.getPrivate(), suite().withInfo(info).withEncapsulation(enc), "part 2/2", c1));
        System.out.println("out of order (message 2 first): " + attempt(recipient.getPrivate(), suite().withInfo(info).withEncapsulation(enc), "part 2/2", c2));
        try {
            Cipher.getInstance("HPKE").init(Cipher.DECRYPT_MODE, recipient.getPrivate(), suite().withInfo(info));
        } catch (Exception e) {
            System.out.println("recipient without the encapsulation: " + e.getClass().getSimpleName());
        }

        // 4. Auth mode: the sender's static key goes into the KEM, so only the
        //    holder of that private key could have produced the ciphertext
        KeyPair alice = kpg.generateKeyPair();
        KeyPair mallory = kpg.generateKeyPair();
        Cipher authSender = Cipher.getInstance("HPKE");
        authSender.init(Cipher.ENCRYPT_MODE, recipient.getPublic(), suite().withInfo(info).withAuthKey(alice.getPrivate()));
        byte[] aliceCiphertext = authSender.doFinal(utf8("from alice"));
        HPKEParameterSpec fromAlice = suite().withInfo(info).withAuthKey(alice.getPublic()).withEncapsulation(authSender.getIV());
        HPKEParameterSpec fromMallory = suite().withInfo(info).withAuthKey(mallory.getPublic()).withEncapsulation(authSender.getIV());
        System.out.println("auth mode, expecting alice:   " + attempt(recipient.getPrivate(), fromAlice, null, aliceCiphertext));
        System.out.println("auth mode, expecting mallory: " + attempt(recipient.getPrivate(), fromMallory, null, aliceCiphertext));
    }

    // Decrypts one message with a fresh recipient context
    static String attempt(PrivateKey key, HPKEParameterSpec spec, String aad, byte[] ciphertext) throws Exception {
        Cipher c = Cipher.getInstance("HPKE");
        c.init(Cipher.DECRYPT_MODE, key, spec);
        if (aad != null) c.updateAAD(utf8(aad));
        try {
            return "\"" + str(c.doFinal(ciphertext)) + "\"";
        } catch (Exception e) {
            return e.getClass().getSimpleName();
        }
    }

    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    static String str(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }
}

/* Output:
RFC 9180 A.1 seq 0: Beauty is truth, truth beauty
RFC 9180 A.1 seq 1: Beauty is truth, truth beauty
encapsulation 32 bytes, ciphertext 30 bytes (14 + 16-byte tag), same plaintext twice gives different ciphertexts: true
decrypted in order: attack at dawn / attack at dawn
wrong info:  AEADBadTagException
wrong AAD:   AEADBadTagException
out of order (message 2 first): AEADBadTagException
recipient without the encapsulation: InvalidAlgorithmParameterException
auth mode, expecting alice:   "from alice"
auth mode, expecting mallory: AEADBadTagException
*/
