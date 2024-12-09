// Misuse: a constant IV ("new byte[16]" or a string in the code)
//
// An IV that never changes turns a randomized mode back into a
// deterministic one. With CBC, messages that start the same produce
// ciphertexts that start the same, block for block, so an observer learns
// which records share a prefix (same user, same command) and can mount
// chosen-plaintext guessing. With CTR or GCM it is far worse: the same
// key + nonce means the same keystream, so the XOR of two ciphertexts is the
// XOR of the plaintexts, and GCM's authentication key leaks too. Typical
// sources: copy-pasted samples with new IvParameterSpec(new byte[16]), an IV
// derived from the key or the password, or a nonce stored in configuration.
// Fix: a fresh random IV/nonce per message from SecureRandom (or a counter
// that can never repeat under a key), stored next to the ciphertext.
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class StaticIv {
    static final byte[] STATIC_IV = new byte[16]; // the bug: all zeros, forever

    public static void main(String[] args) throws Exception {
        SecretKey key = KeyGenerator.getInstance("AES").generateKey();
        String[] records = {
            "user=alice;role=admin;note=xyz..",
            "user=alice;role=admin;note=abc..",
            "user=bobby;role=guest;note=xyz..",
        };

        System.out.println("Noncompliant, CBC with a static IV:");
        byte[][] cts = new byte[records.length][];
        for (int i = 0; i < records.length; i++) cts[i] = cbc(key, STATIC_IV, records[i]);
        System.out.println("  records 0 and 1: equal first block: " + sameFirstBlock(cts[0], cts[1]) + " (same user and role, visible)");
        System.out.println("  records 0 and 2: equal first block: " + sameFirstBlock(cts[0], cts[2]));
        System.out.println("  record 0 stored twice, identical: " + Arrays.equals(cts[0], cbc(key, STATIC_IV, records[0])));

        System.out.println("Compliant, CBC with a random IV per message:");
        SecureRandom rnd = new SecureRandom();
        byte[] iv0 = new byte[16], iv1 = new byte[16];
        rnd.nextBytes(iv0);
        rnd.nextBytes(iv1);
        System.out.println("  records 0 and 1: equal first block: " + sameFirstBlock(cbc(key, iv0, records[0]), cbc(key, iv1, records[1])));

        System.out.println("Noncompliant, CTR with a static IV (same keystream):");
        byte[] a = ctr(key, STATIC_IV, records[0]);
        byte[] b = ctr(key, STATIC_IV, records[2]);
        byte[] knownPlain = records[0].getBytes(StandardCharsets.US_ASCII); // attacker knows record 0
        byte[] recovered = new byte[b.length];
        for (int i = 0; i < b.length; i++) recovered[i] = (byte) (a[i] ^ b[i] ^ knownPlain[i]);
        System.out.println("  record 2 recovered without the key: " + new String(recovered, StandardCharsets.US_ASCII));
    }

    static boolean sameFirstBlock(byte[] x, byte[] y) {
        return Arrays.equals(Arrays.copyOf(x, 16), Arrays.copyOf(y, 16));
    }

    static byte[] cbc(SecretKey key, byte[] iv, String s) throws Exception {
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
        return c.doFinal(s.getBytes(StandardCharsets.US_ASCII));
    }

    static byte[] ctr(SecretKey key, byte[] iv, String s) throws Exception {
        Cipher c = Cipher.getInstance("AES/CTR/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
        return c.doFinal(s.getBytes(StandardCharsets.US_ASCII));
    }
}

/* Output:
Noncompliant, CBC with a static IV:
  records 0 and 1: equal first block: true (same user and role, visible)
  records 0 and 2: equal first block: false
  record 0 stored twice, identical: true
Compliant, CBC with a random IV per message:
  records 0 and 1: equal first block: false
Noncompliant, CTR with a static IV (same keystream):
  record 2 recovered without the key: user=bobby;role=guest;note=xyz..
*/
