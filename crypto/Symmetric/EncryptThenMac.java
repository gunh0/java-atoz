// Encrypt-then-MAC: building authenticated encryption from CBC + HMAC
//
// When a protocol forces a non-AEAD mode (AES-CBC, AES-CTR), add a MAC, and
// compose it in the one safe order: encrypt, then MAC the IV and ciphertext
// (and any header); on receipt, verify the MAC in constant time FIRST and
// only then decrypt. MAC-then-encrypt (TLS before 1.3) and encrypt-and-MAC
// (SSH) both had to decrypt before they could authenticate, which is what
// made padding oracles and similar attacks possible. Use two independent
// keys, here split from one master key with HKDF-style labels, and include
// everything the receiver interprets (version, algorithm, IV) in the MAC.
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class EncryptThenMac {
    static final byte VERSION = 1;
    final SecretKeySpec encKey;
    final SecretKeySpec macKey;

    EncryptThenMac(byte[] master) throws Exception {
        encKey = new SecretKeySpec(label(master, "enc"), "AES");
        macKey = new SecretKeySpec(label(master, "mac"), "HmacSHA256");
    }

    // One HMAC block of HKDF-Expand: enough for a 32-byte key per label
    static byte[] label(byte[] master, String purpose) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(master, "HmacSHA256"));
        mac.update(purpose.getBytes(StandardCharsets.US_ASCII));
        return mac.doFinal(new byte[] {1});
    }

    // version(1) || iv(16) || ciphertext || tag(32)
    byte[] seal(byte[] plaintext) throws Exception {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, encKey, new IvParameterSpec(iv));
        byte[] ct = c.doFinal(plaintext);
        ByteBuffer body = ByteBuffer.allocate(1 + 16 + ct.length).put(VERSION).put(iv).put(ct);
        byte[] tag = mac(body.array());
        return ByteBuffer.allocate(body.capacity() + 32).put(body.array()).put(tag).array();
    }

    byte[] open(byte[] message) throws Exception {
        if (message.length < 1 + 16 + 16 + 32) throw new GeneralSecurityException("message too short");
        byte[] body = Arrays.copyOf(message, message.length - 32);
        byte[] tag = Arrays.copyOfRange(message, message.length - 32, message.length);
        if (!MessageDigest.isEqual(mac(body), tag)) {
            throw new GeneralSecurityException("authentication failed"); // decrypt nothing
        }
        if (body[0] != VERSION) throw new GeneralSecurityException("unsupported version");
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, encKey, new IvParameterSpec(body, 1, 16));
        return c.doFinal(body, 17, body.length - 17);
    }

    byte[] mac(byte[] data) throws Exception {
        Mac m = Mac.getInstance("HmacSHA256");
        m.init(macKey);
        return m.doFinal(data);
    }

    public static void main(String[] args) throws Exception {
        byte[] master = new byte[32];
        new SecureRandom().nextBytes(master);
        EncryptThenMac etm = new EncryptThenMac(master);
        System.out.println("encryption and MAC keys differ: "
                + !Arrays.equals(etm.encKey.getEncoded(), etm.macKey.getEncoded()));

        byte[] sealed = etm.seal("transfer 10 EUR to bob".getBytes(StandardCharsets.US_ASCII));
        System.out.println("22-byte message sealed into " + sealed.length + " bytes (1 + 16 IV + 32 ct + 32 tag)");
        System.out.println("open genuine: " + new String(etm.open(sealed), StandardCharsets.US_ASCII));

        // Every kind of tampering ends at the MAC check, with one message
        for (int pos : new int[] {0, 5, 20, sealed.length - 1}) {
            byte[] bad = sealed.clone();
            bad[pos] ^= 1;
            try {
                etm.open(bad);
                System.out.println("byte " + pos + " flipped: accepted?!");
            } catch (GeneralSecurityException e) {
                System.out.println("byte " + pos + " flipped: " + e.getMessage());
            }
        }
        try {
            etm.open(Arrays.copyOf(sealed, sealed.length - 16)); // truncated
        } catch (GeneralSecurityException e) {
            System.out.println("truncated: " + e.getMessage());
        }
    }
}

/* Output:
encryption and MAC keys differ: true
22-byte message sealed into 81 bytes (1 + 16 IV + 32 ct + 32 tag)
open genuine: transfer 10 EUR to bob
byte 0 flipped: authentication failed
byte 5 flipped: authentication failed
byte 20 flipped: authentication failed
byte 80 flipped: authentication failed
truncated: authentication failed
*/
