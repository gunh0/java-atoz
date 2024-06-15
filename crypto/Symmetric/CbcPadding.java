// AES-CBC with PKCS#5/#7 padding: IVs, padding and what CBC does not give you
//
// CBC XORs each plaintext block with the previous ciphertext block before
// encrypting it; the first block uses the IV. The IV must be unpredictable
// (random per message, sent along in the clear): a fixed or counter IV lets
// an attacker see when two messages start with the same blocks, and a
// predictable IV enabled the BEAST attack on TLS 1.0. Since CBC works on
// whole blocks, "PKCS5Padding" (really PKCS#7) appends 1..16 bytes, each
// equal to the pad length, so ciphertext = IV + plaintext rounded UP to the
// next block. CBC gives confidentiality only: flipping a ciphertext bit
// garbles that block and flips the same bit in the NEXT plaintext block,
// undetected. Unless an old protocol forces CBC, use GCM.
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class CbcPadding {
    static final SecureRandom RANDOM = new SecureRandom();

    public static void main(String[] args) throws Exception {
        SecretKey key = KeyGenerator.getInstance("AES").generateKey();

        // Padding: 0..17 bytes of plaintext -> ciphertext length
        StringBuilder sizes = new StringBuilder();
        for (int n : new int[] {0, 1, 15, 16, 17, 31, 32}) {
            sizes.append(n).append("->").append(encrypt(key, new byte[n]).length - 16).append("  ");
        }
        System.out.println("plaintext->ciphertext bytes (IV excluded): " + sizes.toString().strip());

        // Random IV: the same message encrypts differently every time
        byte[] msg = "attack at dawn, attack at dawn!!".getBytes(StandardCharsets.US_ASCII); // 2 blocks
        System.out.println("same message, two encryptions equal: "
                + Arrays.equals(encrypt(key, msg), encrypt(key, msg)));

        // Malleability: flip bit 0 of ciphertext block 1 (the first block after the IV)
        byte[] blob = encrypt(key, msg);
        blob[16] ^= 0x01;
        byte[] out = decrypt(key, blob);
        String block1 = new String(out, 0, 16, StandardCharsets.ISO_8859_1);
        String block2 = new String(out, 16, 16, StandardCharsets.US_ASCII);
        System.out.println("after flipping one ciphertext bit:");
        System.out.println("  block 1 intact: " + block1.equals("attack at dawn, ") + " (garbled)");
        System.out.println("  block 2: \"" + block2 + "\" (first byte 'a' ^ 1 = '" + (char) ('a' ^ 1) + "')");
        System.out.println("  decryption reported an error: false");

        // Flipping IV bits edits block 1 precisely, without garbling anything
        blob = encrypt(key, msg);
        byte[] target = "defend".getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < target.length; i++) blob[i] ^= (byte) (msg[i] ^ target[i]);
        System.out.println("  edited via the IV: \"" + new String(decrypt(key, blob), StandardCharsets.US_ASCII) + "\"");
    }

    // Output format: IV (16 bytes) || ciphertext
    static byte[] encrypt(SecretKey key, byte[] plaintext) throws Exception {
        byte[] iv = new byte[16];
        RANDOM.nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
        byte[] ct = c.doFinal(plaintext);
        byte[] out = new byte[16 + ct.length];
        System.arraycopy(iv, 0, out, 0, 16);
        System.arraycopy(ct, 0, out, 16, ct.length);
        return out;
    }

    static byte[] decrypt(SecretKey key, byte[] blob) throws Exception {
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(blob, 0, 16));
        return c.doFinal(blob, 16, blob.length - 16);
    }
}

/* Output:
plaintext->ciphertext bytes (IV excluded): 0->16  1->16  15->16  16->32  17->32  31->32  32->48
same message, two encryptions equal: false
after flipping one ciphertext bit:
  block 1 intact: false (garbled)
  block 2: "`ttack at dawn!!" (first byte 'a' ^ 1 = '`')
  decryption reported an error: false
  edited via the IV: "defend at dawn, attack at dawn!!"
*/
