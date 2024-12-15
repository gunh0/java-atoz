// Misuse: one hard-coded encryption key for every installation
//
// CERT MSC03-J (../../secure-coding/MSC/MSC03_HardCodedSecrets.java) shows
// that a key in the source is readable from the class file. The second
// problem is architectural: a key compiled into the product is the SAME key
// at every customer, in every environment, forever. Extract it once (from
// any copy of the binary) and you can decrypt every customer's data and
// forge their tokens; and since rotating it means shipping a new release
// that must still read old data, it never gets rotated. Fix: generate keys
// per installation at setup time (or fetch them from a KMS), keep them out
// of the code, and tag every ciphertext with a key ID so keys can be
// rotated while old data stays readable.
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class HardcodedKey {
    // Noncompliant: shipped in every copy of the product
    static final byte[] PRODUCT_KEY = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

    public static void main(String[] args) throws Exception {
        // Customer A encrypts its data; an attacker owns a copy of the product (customer B's)
        byte[] dataOfA = seal(new SecretKeySpec(PRODUCT_KEY, "AES"), (byte) 0, "A's payroll: 1.2M");
        System.out.println("noncompliant: B's installation decrypts A's data: "
                + open(new SecretKeySpec(PRODUCT_KEY, "AES"), dataOfA));

        // Compliant: each installation generates its own key ring at setup
        Installation a = new Installation(), b = new Installation();
        byte[] fromA = a.encrypt("A's payroll: 1.2M");
        System.out.println("compliant: A decrypts its own data: " + a.decrypt(fromA));
        System.out.println("compliant: B decrypts A's data: " + b.decrypt(fromA));

        // Rotation: new data uses key 2, old data (key ID 1) stays readable
        a.rotate();
        byte[] newer = a.encrypt("A's payroll: 1.3M");
        System.out.println("after rotation: key IDs in use " + fromA[0] + " (old) and " + newer[0] + " (new)");
        System.out.println("after rotation: old record still readable: " + a.decrypt(fromA));
    }

    static class Installation {
        private final Map<Byte, SecretKey> keys = new HashMap<>();
        private byte current;

        Installation() throws Exception { rotate(); }

        void rotate() throws Exception {
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(256);
            keys.put(++current, kg.generateKey());
        }

        byte[] encrypt(String s) throws Exception { return seal(keys.get(current), current, s); }

        String decrypt(byte[] blob) throws Exception {
            SecretKey k = keys.get(blob[0]);
            return k == null ? "unknown key id" : open(k, blob);
        }
    }

    // keyId(1) || nonce(12) || ciphertext+tag; the key ID is authenticated as AAD
    static byte[] seal(SecretKey key, byte keyId, String s) throws Exception {
        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        c.updateAAD(new byte[] {keyId});
        byte[] ct = c.doFinal(s.getBytes(StandardCharsets.UTF_8));
        return ByteBuffer.allocate(1 + 12 + ct.length).put(keyId).put(nonce).put(ct).array();
    }

    static String open(SecretKey key, byte[] blob) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, blob, 1, 12));
        c.updateAAD(new byte[] {blob[0]});
        try {
            return "\"" + new String(c.doFinal(blob, 13, blob.length - 13), StandardCharsets.UTF_8) + "\"";
        } catch (AEADBadTagException e) {
            return "rejected (" + e.getClass().getSimpleName() + ")";
        }
    }
}

/* Output:
noncompliant: B's installation decrypts A's data: "A's payroll: 1.2M"
compliant: A decrypts its own data: "A's payroll: 1.2M"
compliant: B decrypts A's data: rejected (AEADBadTagException)
after rotation: key IDs in use 1 (old) and 2 (new)
after rotation: old record still readable: "A's payroll: 1.2M"
*/
