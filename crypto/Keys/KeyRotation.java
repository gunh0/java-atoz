// Key rotation with versioned ciphertexts: a key ID in every message
//
// Keys must be rotatable: on a schedule, after an incident, when an employee
// with access leaves, before a usage limit (AES-GCM with random IVs: about
// 2^32 messages per key). Rotation is only painless if every ciphertext says
// which key made it. The format used here, like Tink's output prefix, is
//   [format version: 1 byte][key id: 4 bytes][IV: 12 bytes][ciphertext+tag]
// and the 5-byte header is also the AES-GCM AAD, so it cannot be edited to
// point at another key. A keyring holds one PRIMARY key (used to encrypt) and
// older keys that may still decrypt. Rotation = add a new primary; old data
// still opens; a background job (or each read) re-encrypts under the primary;
// when no ciphertext references an old key, retire it. The anti-pattern is an
// unversioned format whose reader "tries every key until one works": it costs
// a decryption per key and you can never tell which keys are still in use.
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class KeyRotation {
    static final byte FORMAT_V1 = 1;
    static final SecureRandom RNG = new SecureRandom();

    static final class Keyring {
        private final Map<Integer, SecretKey> keys = new TreeMap<>();
        private int primary;

        int rotate() throws GeneralSecurityException {
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(256);
            primary = keys.size() + 1; // ids would normally be random to avoid collisions across keyrings
            keys.put(primary, kg.generateKey());
            return primary;
        }

        void retire(int id) {
            if (id == primary) throw new IllegalStateException("cannot retire the primary key");
            keys.remove(id);
        }

        byte[] encrypt(String plaintext) throws GeneralSecurityException {
            byte[] header = ByteBuffer.allocate(5).put(FORMAT_V1).putInt(primary).array();
            byte[] iv = new byte[12];
            RNG.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, keys.get(primary), new GCMParameterSpec(128, iv));
            c.updateAAD(header);
            byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(5 + 12 + ct.length).put(header).put(iv).put(ct).array();
        }

        String decrypt(byte[] message) throws GeneralSecurityException {
            ByteBuffer b = ByteBuffer.wrap(message);
            byte version = b.get();
            if (version != FORMAT_V1) throw new GeneralSecurityException("unknown format version " + version);
            int keyId = b.getInt();
            SecretKey key = keys.get(keyId);
            if (key == null) throw new GeneralSecurityException("key " + keyId + " is not in the keyring");
            byte[] iv = new byte[12];
            b.get(iv);
            byte[] ct = new byte[b.remaining()];
            b.get(ct);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            c.updateAAD(message, 0, 5);
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        }

        static int keyIdOf(byte[] message) { return ByteBuffer.wrap(message, 1, 4).getInt(); }
    }

    static String tryDecrypt(Keyring ring, byte[] message) {
        try {
            return ring.decrypt(message);
        } catch (AEADBadTagException e) {
            return "<rejected: authentication failed>";
        } catch (GeneralSecurityException e) {
            return "<rejected: " + e.getMessage() + ">";
        }
    }

    public static void main(String[] args) throws Exception {
        Keyring ring = new Keyring();
        ring.rotate();
        List<byte[]> store = new ArrayList<>();
        store.add(ring.encrypt("record 1"));
        store.add(ring.encrypt("record 2"));

        int k2 = ring.rotate(); // scheduled rotation: new primary
        store.add(ring.encrypt("record 3"));
        System.out.println("primary is now key " + k2);
        for (byte[] m : store) System.out.println("key " + Keyring.keyIdOf(m) + ": " + tryDecrypt(ring, m));

        // Editing the header to claim another key breaks the tag (the header is AAD)
        byte[] forged = store.get(0).clone();
        ByteBuffer.wrap(forged).putInt(1, k2);
        System.out.println("header edited to key " + k2 + ": " + tryDecrypt(ring, forged));
        byte[] future = store.get(0).clone();
        future[0] = 2;
        System.out.println("format version 2:        " + tryDecrypt(ring, future));

        byte[] oldBackup = store.get(0).clone(); // a stale copy that the migration will miss

        // Re-encryption job: move everything to the primary key
        int migrated = 0;
        for (int i = 0; i < store.size(); i++) {
            if (Keyring.keyIdOf(store.get(i)) != k2) {
                store.set(i, ring.encrypt(ring.decrypt(store.get(i))));
                migrated++;
            }
        }
        System.out.println("re-encrypted " + migrated + " records; any still on key 1: "
                + store.stream().anyMatch(m -> Keyring.keyIdOf(m) == 1));

        // Only now is retiring key 1 safe
        ring.retire(1);
        for (byte[] m : store) System.out.println("key " + Keyring.keyIdOf(m) + ": " + tryDecrypt(ring, m));
        System.out.println("stale backup on key 1: " + tryDecrypt(ring, oldBackup));
        try {
            ring.retire(k2);
        } catch (IllegalStateException e) {
            System.out.println("retire primary: " + e.getMessage());
        }
    }
}

/* Output:
primary is now key 2
key 1: record 1
key 1: record 2
key 2: record 3
header edited to key 2: <rejected: authentication failed>
format version 2:        <rejected: unknown format version 2>
re-encrypted 2 records; any still on key 1: false
key 2: record 1
key 2: record 2
key 2: record 3
stale backup on key 1: <rejected: key 1 is not in the keyring>
retire primary: cannot retire the primary key
*/
