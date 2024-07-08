// Nonce reuse: what happens when a CTR/GCM keystream is used twice
//
// CTR and GCM derive the keystream from (key, nonce) only. Encrypt two
// messages with the same pair and C1 ^ C2 = P1 ^ P2: the key cancels out.
// Anyone who knows or guesses one plaintext (a fixed header, a known file
// format, a "Hello") reads the other one, and with GCM the authentication
// key H leaks as well, letting the attacker forge tags (the "forbidden
// attack"; real TLS servers were caught reusing GCM nonces in 2016). The
// usual causes: a constant IV in code, a counter reset after a restart, or
// a random nonce that is too short. Here the second encryption uses a fresh
// Cipher object, which is exactly how the JDK's same-IV guard gets bypassed
// in real code.
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class NonceReuse {
    public static void main(String[] args) throws Exception {
        SecretKey key = KeyGenerator.getInstance("AES").generateKey();
        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce); // random, but then reused: the bug

        String p1 = "GET /index.html HTTP/1.1 Host: a";
        String p2 = "password=hunter2&user=admin&ok=1";
        byte[] c1 = gcm(key, nonce, p1);
        byte[] c2 = gcm(key, nonce, p2);

        // The attacker has c1, c2 (without tags: GCM ciphertext is CTR output)
        // and guesses p1, a predictable HTTP request line.
        byte[] guess = p1.getBytes(StandardCharsets.US_ASCII);
        byte[] recovered = new byte[guess.length];
        for (int i = 0; i < guess.length; i++) recovered[i] = (byte) (c1[i] ^ c2[i] ^ guess[i]);
        System.out.println("same nonce, recovered p2: " + new String(recovered, StandardCharsets.US_ASCII));

        // Without knowing p1, c1 ^ c2 still leaks where the plaintexts agree
        int equal = 0;
        for (int i = 0; i < guess.length; i++) if (c1[i] == c2[i]) equal++;
        System.out.println("positions where c1 == c2 (and so p1 == p2): " + equal);

        // Fresh nonce per message: the same trick yields noise
        byte[] nonce2 = nonce.clone();
        nonce2[11] ^= 1;
        byte[] c3 = gcm(key, nonce2, p2);
        for (int i = 0; i < guess.length; i++) recovered[i] = (byte) (c1[i] ^ c3[i] ^ guess[i]);
        System.out.println("fresh nonce, recovered p2 correct: "
                + new String(recovered, StandardCharsets.ISO_8859_1).equals(p2));
    }

    static byte[] gcm(SecretKey key, byte[] nonce, String plaintext) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); // a new object: no reuse check
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        return c.doFinal(plaintext.getBytes(StandardCharsets.US_ASCII));
    }
}

/* Output:
same nonce, recovered p2: password=hunter2&user=admin&ok=1
positions where c1 == c2 (and so p1 == p2): 2
fresh nonce, recovered p2 correct: false
*/
