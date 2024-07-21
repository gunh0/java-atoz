// AES Key Wrap: encrypting keys with keys (RFC 3394 / NIST SP 800-38F)
//
// Envelope encryption stores data under a data-encryption key (DEK) and
// stores the DEK encrypted under a key-encryption key (KEK) that lives in a
// KMS or HSM. Key material is short and high-entropy, so a dedicated,
// deterministic mode exists for it: AES Key Wrap needs no nonce (nothing to
// get wrong), adds an 8-byte integrity check and fails on any modification.
// Java exposes it as Cipher "AESWrap" (RFC 3394, multiples of 8 bytes) and
// "AESWrapPad" (RFC 5649, any length) with Cipher.WRAP_MODE / UNWRAP_MODE,
// which take and return Key objects so key bytes need not pass through
// your code. For wrapping with a public key, use RSA-OAEP or a KEM.
import java.security.InvalidKeyException;
import java.security.Key;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class AesKeyWrap {
    public static void main(String[] args) throws Exception {
        HexFormat hex = HexFormat.of();
        // RFC 3394 section 4.1: wrap 128 bits of key data with a 128-bit KEK
        SecretKey kek = new SecretKeySpec(hex.parseHex("000102030405060708090a0b0c0d0e0f"), "AES");
        SecretKey keyData = new SecretKeySpec(hex.parseHex("00112233445566778899aabbccddeeff"), "AES");
        Cipher wrap = Cipher.getInstance("AESWrap");
        wrap.init(Cipher.WRAP_MODE, kek);
        byte[] wrapped = wrap.wrap(keyData);
        System.out.println("RFC 3394 4.1: " + hex.formatHex(wrapped) + " "
                + (hex.formatHex(wrapped).equals("1fa68b0a8112b447aef34bd8fb5a7b829d3e862371d2cfe5") ? "OK" : "MISMATCH"));

        // Envelope encryption: random DEK, wrapped under the KEK
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256);
        SecretKey dek = kg.generateKey();
        wrap.init(Cipher.WRAP_MODE, kek);
        byte[] wrappedDek = wrap.wrap(dek);
        System.out.println("256-bit DEK wraps to " + wrappedDek.length + " bytes (key + 8-byte check)");

        Cipher unwrap = Cipher.getInstance("AESWrap");
        unwrap.init(Cipher.UNWRAP_MODE, kek);
        Key back = unwrap.unwrap(wrappedDek, "AES", Cipher.SECRET_KEY);
        System.out.println("unwrapped equals DEK: " + back.equals(dek));

        // Integrity: one flipped bit and unwrapping fails
        wrappedDek[5] ^= 1;
        try {
            unwrap.init(Cipher.UNWRAP_MODE, kek);
            unwrap.unwrap(wrappedDek, "AES", Cipher.SECRET_KEY);
            System.out.println("tampered DEK accepted");
        } catch (InvalidKeyException e) {
            System.out.println("tampered wrapped key: " + e.getClass().getSimpleName());
        }

        // AESWrapPad (RFC 5649) takes lengths that are not multiples of 8
        Cipher pad = Cipher.getInstance("AESWrapPad");
        pad.init(Cipher.WRAP_MODE, kek);
        byte[] w20 = pad.wrap(new SecretKeySpec(new byte[20], "HmacSHA1"));
        System.out.println("AESWrapPad: 20-byte key wraps to " + w20.length + " bytes");
        // Deterministic: the same key under the same KEK always wraps the same
        wrap.init(Cipher.WRAP_MODE, kek);
        System.out.println("wrapping is deterministic: " + hex.formatHex(wrap.wrap(keyData)).equals(hex.formatHex(wrapped)));
    }
}

/* Output:
RFC 3394 4.1: 1fa68b0a8112b447aef34bd8fb5a7b829d3e862371d2cfe5 OK
256-bit DEK wraps to 40 bytes (key + 8-byte check)
unwrapped equals DEK: true
tampered wrapped key: InvalidKeyException
AESWrapPad: 20-byte key wraps to 32 bytes
wrapping is deterministic: true
*/
