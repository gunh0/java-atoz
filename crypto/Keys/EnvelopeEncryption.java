// Envelope encryption: a KEK in the KMS, a fresh DEK per object
//
// Cloud KMSs and HSMs never hand out their master keys and are slow and
// rate-limited, so data is not encrypted by them directly. Instead, for each
// object you ask the KMS for a data-encryption key (DEK): it returns the DEK
// in plaintext AND wrapped under a key-encryption key (KEK) that never leaves
// the KMS. You encrypt locally with the DEK, store the wrapped DEK next to
// the ciphertext and forget the plaintext DEK. To decrypt, send the wrapped
// DEK back to the KMS. The payoffs, all shown below: one KMS call per object
// rather than per byte; an "encryption context" bound into the wrap as AAD,
// so a DEK cannot be unwrapped for another tenant or purpose; KEK rotation
// re-wraps the small DEKs without touching the data; and deleting a KEK
// "crypto-shreds" everything under it. The KMS here is an in-process
// stand-in; Symmetric/AesKeyWrap.java covers the RFC 3394 wrapping mode.
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class EnvelopeEncryption {
    static final SecureRandom RNG = new SecureRandom();

    static byte[] gcm(int mode, SecretKey key, byte[] iv, byte[] aad, byte[] in) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(mode, key, new GCMParameterSpec(128, iv));
        c.updateAAD(aad);
        return c.doFinal(in);
    }

    static byte[] iv() {
        byte[] iv = new byte[12];
        RNG.nextBytes(iv);
        return iv;
    }

    /** Stand-in for a KMS: KEKs stay inside; only wrap/unwrap results leave. */
    static final class Kms {
        private final Map<String, SecretKey> keks = new HashMap<>();
        int calls;

        void createKek(String id) throws GeneralSecurityException {
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(256);
            keks.put(id, kg.generateKey());
        }

        void deleteKek(String id) { keks.remove(id); }

        record DataKey(SecretKey plaintext, byte[] wrapped) {}

        DataKey generateDataKey(String kekId, String context) throws GeneralSecurityException {
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(256);
            SecretKey dek = kg.generateKey();
            return new DataKey(dek, wrap(kekId, dek.getEncoded(), context));
        }

        byte[] wrap(String kekId, byte[] dek, String context) throws GeneralSecurityException {
            calls++;
            byte[] iv = iv();
            byte[] w = gcm(Cipher.ENCRYPT_MODE, kek(kekId), iv, aad(kekId, context), dek);
            return ByteBuffer.allocate(12 + w.length).put(iv).put(w).array();
        }

        SecretKey unwrap(String kekId, byte[] wrapped, String context) throws GeneralSecurityException {
            calls++;
            byte[] dek = gcm(Cipher.DECRYPT_MODE, kek(kekId), Arrays.copyOf(wrapped, 12), aad(kekId, context),
                    Arrays.copyOfRange(wrapped, 12, wrapped.length));
            return new SecretKeySpec(dek, "AES");
        }

        private SecretKey kek(String id) throws GeneralSecurityException {
            SecretKey k = keks.get(id);
            if (k == null) throw new GeneralSecurityException("no such KEK: " + id);
            return k;
        }

        private static byte[] aad(String kekId, String context) {
            return (kekId + "|" + context).getBytes(StandardCharsets.UTF_8);
        }
    }

    /** What gets stored: everything except a plaintext key. */
    record Envelope(String kekId, String context, byte[] wrappedDek, byte[] iv, byte[] ciphertext) {}

    static Envelope encrypt(Kms kms, String kekId, String context, String plaintext) throws GeneralSecurityException {
        Kms.DataKey dk = kms.generateDataKey(kekId, context);
        byte[] iv = iv();
        byte[] ct = gcm(Cipher.ENCRYPT_MODE, dk.plaintext(), iv, context.getBytes(StandardCharsets.UTF_8),
                plaintext.getBytes(StandardCharsets.UTF_8));
        return new Envelope(kekId, context, dk.wrapped(), iv, ct); // dk.plaintext() goes out of scope
    }

    static String decrypt(Kms kms, Envelope e, String context) {
        try {
            SecretKey dek = kms.unwrap(e.kekId(), e.wrappedDek(), context);
            return new String(gcm(Cipher.DECRYPT_MODE, dek, e.iv(), context.getBytes(StandardCharsets.UTF_8),
                    e.ciphertext()), StandardCharsets.UTF_8);
        } catch (AEADBadTagException ex) {
            return "<refused: context does not match>";
        } catch (GeneralSecurityException ex) {
            return "<refused: " + ex.getMessage() + ">";
        }
    }

    public static void main(String[] args) throws Exception {
        Kms kms = new Kms();
        kms.createKek("tenant-a/v1");
        kms.createKek("tenant-b/v1");

        Envelope a = encrypt(kms, "tenant-a/v1", "tenant=a;table=patients", "alice: blood type O-");
        Envelope b = encrypt(kms, "tenant-b/v1", "tenant=b;table=patients", "bob: blood type AB+");
        System.out.println("wrapped DEK bytes (iv + key + tag): " + a.wrappedDek().length);
        System.out.println("decrypt a: " + decrypt(kms, a, "tenant=a;table=patients"));
        System.out.println("decrypt b: " + decrypt(kms, b, "tenant=b;table=patients"));

        // Confused deputy: tenant b's service asks to unwrap tenant a's DEK under its own context
        System.out.println("a with b's context: " + decrypt(kms, a, "tenant=b;table=patients"));

        // One KMS call per object, whatever the size
        kms.calls = 0;
        Envelope big = encrypt(kms, "tenant-a/v1", "tenant=a;table=scans", "x".repeat(5_000_000));
        System.out.println("5 MB object: KMS calls = " + kms.calls + ", ciphertext bytes = " + big.ciphertext().length);

        // KEK rotation: re-wrap the DEK under v2, data ciphertext untouched
        kms.createKek("tenant-a/v2");
        SecretKey dek = kms.unwrap(a.kekId(), a.wrappedDek(), a.context());
        Envelope rotated = new Envelope("tenant-a/v2", a.context(),
                kms.wrap("tenant-a/v2", dek.getEncoded(), a.context()), a.iv(), a.ciphertext());
        System.out.println("after rotation, ciphertext unchanged: " + Arrays.equals(a.ciphertext(), rotated.ciphertext()));
        System.out.println("decrypt rotated: " + decrypt(kms, rotated, a.context()));

        // Crypto-shredding: delete tenant b's KEK and its data is gone, backups included
        kms.deleteKek("tenant-b/v1");
        System.out.println("b after KEK deletion: " + decrypt(kms, b, "tenant=b;table=patients"));
    }
}

/* Output:
wrapped DEK bytes (iv + key + tag): 60
decrypt a: alice: blood type O-
decrypt b: bob: blood type AB+
a with b's context: <refused: context does not match>
5 MB object: KMS calls = 1, ciphertext bytes = 5000016
after rotation, ciphertext unchanged: true
decrypt rotated: alice: blood type O-
b after KEK deletion: <refused: no such KEK: tenant-b/v1>
*/
