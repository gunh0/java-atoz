// The padding oracle: how one bit of error information breaks CBC
//
// A server that decrypts AES-CBC and answers differently for "bad padding"
// and "padding fine, but the content is wrong" (different error codes,
// messages, or even response times) is an oracle. Because flipping a byte of
// the previous block flips the same byte of the plaintext (CbcPadding.java),
// an attacker can submit modified ciphertexts and learn, from which ones
// have valid padding, the plaintext byte by byte, about 128 queries per byte
// and no key needed (Vaudenay 2002; ASP.NET 2010, Lucky13 and POODLE 2013-14).
// This program only shows the leak for one byte; it is not an attack tool.
// The fix is authentication BEFORE decryption: encrypt-then-MAC or an AEAD
// mode such as GCM, where every modified ciphertext fails the same way.
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;

public class PaddingOracleConcept {
    public static void main(String[] args) throws Exception {
        SecretKey key = KeyGenerator.getInstance("AES").generateKey();
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);

        // 16 bytes of plaintext -> a whole extra block of padding (sixteen 0x10)
        byte[] plaintext = "PIN=4711;ACCT=42".getBytes(StandardCharsets.US_ASCII);
        Cipher enc = Cipher.getInstance("AES/CBC/PKCS5Padding");
        enc.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
        byte[] ct = enc.doFinal(plaintext); // 32 bytes: C1 || C2(padding block)

        // The attacker XORs a guess into the last byte of C1, which XORs into
        // the last padding byte, and asks the vulnerable server about it.
        List<Integer> accepted = new ArrayList<>();
        for (int delta = 0; delta < 256; delta++) {
            byte[] modified = ct.clone();
            modified[15] ^= (byte) delta;
            if (!vulnerableServer(key, iv, modified).equals("PADDING_ERROR")) accepted.add(delta);
        }
        System.out.println("vulnerable server: " + accepted.size() + " of 256 guesses had valid padding: "
                + accepted.stream().map(d -> String.format("0x%02x", d)).toList());
        // delta 0x00 is the original. Valid padding for delta 0x11 means the
        // byte became 0x01, so it WAS 0x11 ^ 0x01 = 0x10: one byte learned
        // without the key. Repeating this walks through every block.
        int learned = accepted.get(accepted.size() - 1) ^ 0x01;
        System.out.printf("learned last byte of the padding block: 0x%02x (true value 0x10)%n", learned);

        // AEAD: the tag is checked first, so every forgery looks the same.
        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
        gcm.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        byte[] sealed = gcm.doFinal(plaintext);
        int rejected = 0;
        for (int delta = 1; delta < 256; delta++) {
            byte[] modified = sealed.clone();
            modified[15] ^= (byte) delta;
            try {
                Cipher d = Cipher.getInstance("AES/GCM/NoPadding");
                d.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
                d.doFinal(modified);
            } catch (AEADBadTagException e) {
                rejected++;
            }
        }
        System.out.println("AES-GCM: " + rejected + " of 255 modified ciphertexts rejected with the same error");
    }

    // Distinguishes padding errors from content errors: that difference is the bug.
    static String vulnerableServer(SecretKey key, byte[] iv, byte[] ct) throws Exception {
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        try {
            byte[] p = c.doFinal(ct);
            return p.length == 16 && p[0] == 'P' ? "OK" : "INVALID_MESSAGE";
        } catch (BadPaddingException e) {
            return "PADDING_ERROR";
        }
    }
}

/* Output:
vulnerable server: 2 of 256 guesses had valid padding: [0x00, 0x11]
learned last byte of the padding block: 0x10 (true value 0x10)
AES-GCM: 255 of 255 modified ciphertexts rejected with the same error
*/
