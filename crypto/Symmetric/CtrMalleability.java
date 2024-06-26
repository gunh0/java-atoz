// AES-CTR: a stream cipher, and why encryption is not integrity
//
// CTR mode encrypts a counter (nonce || block number) and XORs the result,
// the keystream, into the plaintext. It needs no padding, is parallel and
// seekable, and is the core of GCM. But ciphertext = plaintext XOR keystream,
// so flipping ciphertext bit i flips plaintext bit i, exactly and silently.
// An attacker who knows (or guesses) the format of a message can rewrite
// fields without the key: here a payment amount and recipient. Decryption
// "succeeds" because there is nothing to check. Never use CTR (or CBC)
// without a MAC; use GCM or ChaCha20-Poly1305, which are CTR-style
// encryption plus an authenticator.
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class CtrMalleability {
    public static void main(String[] args) throws Exception {
        SecretKey key = KeyGenerator.getInstance("AES").generateKey();
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);

        String order = "to=alice;amount=00010;";
        Cipher enc = Cipher.getInstance("AES/CTR/NoPadding");
        enc.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
        byte[] ct = enc.doFinal(order.getBytes(StandardCharsets.US_ASCII));
        System.out.println("plaintext " + order.length() + " bytes -> ciphertext " + ct.length + " bytes (no padding)");

        // Attacker: knows the format, not the key. XOR (old ^ new) into the ciphertext.
        String forged = "to=mallo;amount=99999;";
        for (int i = 0; i < ct.length; i++) {
            ct[i] ^= (byte) (order.charAt(i) ^ forged.charAt(i));
        }

        Cipher dec = Cipher.getInstance("AES/CTR/NoPadding");
        dec.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        String result = new String(dec.doFinal(ct), StandardCharsets.US_ASCII);
        System.out.println("receiver decrypts: " + result);
        System.out.println("receiver noticed anything: false");

        // Keystream position is all that matters: decrypting from the middle
        // works by starting the counter at block 1 (CTR is seekable).
        byte[] ivPlusOne = iv.clone();
        for (int i = 15; i >= 0 && ++ivPlusOne[i] == 0; i--) { } // big-endian increment
        Cipher seek = Cipher.getInstance("AES/CTR/NoPadding");
        seek.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(ivPlusOne));
        byte[] tail = seek.doFinal(Arrays.copyOfRange(ct, 16, ct.length));
        System.out.println("decrypt from block 1 only: " + new String(tail, StandardCharsets.US_ASCII));
    }
}

/* Output:
plaintext 22 bytes -> ciphertext 22 bytes (no padding)
receiver decrypts: to=mallo;amount=99999;
receiver noticed anything: false
decrypt from block 1 only: 99999;
*/
