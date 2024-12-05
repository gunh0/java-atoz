// Misuse: Cipher.getInstance("AES") silently means AES/ECB/PKCS5Padding
//
// A transformation is "algorithm/mode/padding". If you give only the
// algorithm, the provider picks its default mode and padding, and for
// SunJCE (and most other providers) that is ECB with PKCS#5 padding: no IV,
// no randomization, no integrity. The code compiles, round-trips correctly
// in every test, and leaks equal blocks and equal messages in production.
// Static analyzers flag it (e.g. CWE-327, SpotBugs CIPHER_INTEGRITY /
// ECB_MODE), but the reliable fix is a habit: always spell out the full
// transformation, and make it an AEAD one: "AES/GCM/NoPadding" with a fresh
// 12-byte nonce per message. The same applies to "RSA" (defaults to
// PKCS#1 v1.5 padding) and "DES"/"DESede" (simply too weak).
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class DefaultEcbTransformation {
    public static void main(String[] args) throws Exception {
        SecretKey key = KeyGenerator.getInstance("AES").generateKey();
        byte[] msg = "card=4111111111111111;card=4111111111111111;".getBytes(StandardCharsets.US_ASCII);

        // Noncompliant: looks fine, works fine, is ECB
        Cipher bad = Cipher.getInstance("AES");
        bad.init(Cipher.ENCRYPT_MODE, key);
        byte[] c1 = bad.doFinal(msg);
        byte[] c2 = bad.doFinal(msg);
        System.out.println("\"AES\": IV used: " + (bad.getIV() != null));
        System.out.println("\"AES\": same message twice -> same ciphertext: " + Arrays.equals(c1, c2));
        System.out.println("\"AES\": behaves exactly like AES/ECB/PKCS5Padding: " + Arrays.equals(c1, ecb(key, msg)));
        // Repeated 16-byte blocks inside one message show up directly
        byte[] aligned = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
        byte[] ca = bad.doFinal(aligned);
        System.out.println("\"AES\": two equal plaintext blocks -> equal ciphertext blocks: "
                + Arrays.equals(Arrays.copyOfRange(ca, 0, 16), Arrays.copyOfRange(ca, 16, 32)));

        // Compliant: explicit AEAD transformation, fresh nonce each time
        byte[] n1 = new byte[12], n2 = new byte[12];
        SecureRandom rnd = new SecureRandom();
        rnd.nextBytes(n1);
        rnd.nextBytes(n2);
        byte[] g1 = gcm(key, n1, msg), g2 = gcm(key, n2, msg);
        System.out.println("AES/GCM/NoPadding: same message twice -> same ciphertext: " + Arrays.equals(g1, g2));
        System.out.println("AES/GCM/NoPadding: output = message + 16-byte tag: " + (g1.length == msg.length + 16));

        // Same story for RSA: "RSA" is RSA/ECB/PKCS1Padding, not OAEP
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        Cipher rsa = Cipher.getInstance("RSA");
        rsa.init(Cipher.ENCRYPT_MODE, kp.getPublic());
        byte[] rc = rsa.doFinal(msg);
        Cipher v15 = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        v15.init(Cipher.DECRYPT_MODE, kp.getPrivate());
        System.out.println("\"RSA\": decrypts as RSA/ECB/PKCS1Padding: " + Arrays.equals(v15.doFinal(rc), msg));
    }

    static byte[] ecb(SecretKey key, byte[] msg) throws Exception {
        Cipher c = Cipher.getInstance("AES/ECB/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, key);
        return c.doFinal(msg);
    }

    static byte[] gcm(SecretKey key, byte[] nonce, byte[] msg) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        return c.doFinal(msg);
    }
}

/* Output:
"AES": IV used: false
"AES": same message twice -> same ciphertext: true
"AES": behaves exactly like AES/ECB/PKCS5Padding: true
"AES": two equal plaintext blocks -> equal ciphertext blocks: true
AES/GCM/NoPadding: same message twice -> same ciphertext: false
AES/GCM/NoPadding: output = message + 16-byte tag: true
"RSA": decrypts as RSA/ECB/PKCS1Padding: true
*/
