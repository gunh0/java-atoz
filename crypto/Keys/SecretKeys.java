// Secret keys: KeyGenerator, SecretKeySpec and SecretKeyFactory
//
// There are three ways to get a javax.crypto.SecretKey, for three situations.
// KeyGenerator creates a fresh random key of the right size for an
// algorithm (use this whenever you can). SecretKeySpec wraps bytes you
// already have (from a KDF, a key store, a key exchange); it checks nothing,
// so a 5-byte "AES key" is only rejected later by Cipher.init. A
// SecretKeyFactory converts between key representations: the important one
// is PBKDF2, which turns a PBEKeySpec (password + salt + iterations) into key
// bytes. All of them are "RAW" byte strings underneath. Never derive a key
// by calling getBytes() on a passphrase.
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class SecretKeys {
    public static void main(String[] args) throws Exception {
        // 1. KeyGenerator: random key of a chosen size (128, 192 or 256 for AES)
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256);
        SecretKey generated = kg.generateKey();
        describe("KeyGenerator AES-256", generated);

        KeyGenerator hk = KeyGenerator.getInstance("HmacSHA256");
        describe("KeyGenerator HmacSHA256 (default size)", hk.generateKey());

        try {
            kg.init(100);
        } catch (InvalidParameterException e) {
            System.out.println("KeyGenerator AES-100: " + e.getClass().getSimpleName());
        }

        // 2. SecretKeySpec: wrap existing bytes; validation happens at use
        byte[] raw = new byte[16];
        new SecureRandom().nextBytes(raw);
        describe("SecretKeySpec from 16 bytes", new SecretKeySpec(raw, "AES"));

        SecretKey tooShort = new SecretKeySpec("hello".getBytes(StandardCharsets.US_ASCII), "AES");
        try {
            Cipher.getInstance("AES/GCM/NoPadding").init(Cipher.ENCRYPT_MODE, tooShort);
        } catch (InvalidKeyException e) {
            System.out.println("5-byte AES key rejected at Cipher.init: " + e.getClass().getSimpleName());
        }

        // 3. SecretKeyFactory: password -> key bytes via PBKDF2, then re-label
        //    the bytes as an AES key (the factory's own key says "PBKDF2...")
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        PBEKeySpec spec = new PBEKeySpec("correct horse".toCharArray(), salt, 210_000, 256);
        SecretKey pbk = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec);
        spec.clearPassword();
        describe("SecretKeyFactory PBKDF2 result", pbk);
        SecretKey aesFromPassword = new SecretKeySpec(pbk.getEncoded(), "AES");
        describe("  re-labelled for AES", aesFromPassword);

        // Factories also go the other way, key -> spec. Note what comes back:
        // the PBKDF2 key object still holds the password, so do not keep it
        // around; copy the bytes you need (as above) and drop it.
        PBEKeySpec back = (PBEKeySpec) SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .getKeySpec(pbk, PBEKeySpec.class);
        System.out.println("PBKDF2 key -> PBEKeySpec: iterations=" + back.getIterationCount()
                + ", salt bytes=" + back.getSalt().length + ", password recovered="
                + new String(back.getPassword()).equals("correct horse"));
        back.clearPassword();
    }

    static void describe(String label, SecretKey key) {
        System.out.printf("%-40s algorithm=%s format=%s bits=%d%n",
                label, key.getAlgorithm(), key.getFormat(), key.getEncoded().length * 8);
    }
}

/* Output:
KeyGenerator AES-256                     algorithm=AES format=RAW bits=256
KeyGenerator HmacSHA256 (default size)   algorithm=HmacSHA256 format=RAW bits=256
KeyGenerator AES-100: InvalidParameterException
SecretKeySpec from 16 bytes              algorithm=AES format=RAW bits=128
5-byte AES key rejected at Cipher.init: InvalidKeyException
SecretKeyFactory PBKDF2 result           algorithm=PBKDF2WithHmacSHA256 format=RAW bits=256
  re-labelled for AES                    algorithm=AES format=RAW bits=256
PBKDF2 key -> PBEKeySpec: iterations=210000, salt bytes=16, password recovered=true
*/
