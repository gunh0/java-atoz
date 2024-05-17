// PBKDF2: turning a password into a key (RFC 8018, vectors RFC 6070)
//
// Passwords have little entropy, so a key derived from one must be slow and
// salted. PBKDF2 iterates an HMAC c times per output block: the salt (random,
// at least 16 bytes, stored next to the result) makes every user's
// derivation different, so precomputed tables and "crack all users at once"
// fail; the iteration count multiplies the attacker's cost per guess. Choose
// it as high as your login latency allows (OWASP's 2023 guidance for
// PBKDF2-HMAC-SHA256 is 600,000). Ask for at most the hash's output length:
// a longer dkLen costs you extra blocks but the attacker only needs one.
// PBKDF2 is not memory-hard (GPUs love it); prefer scrypt or Argon2 where
// available, but PBKDF2 is the one in the JDK and in FIPS 140.
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class Pbkdf2KnownAnswer {
    public static void main(String[] args) throws Exception {
        // RFC 6070 (PBKDF2-HMAC-SHA1)
        kat("password", "salt", 1, 20, "0c60c80f961f0e71f3a9b524af6012062fe037a6");
        kat("password", "salt", 2, 20, "ea6c014dc72d6f8ccd1ed92ace1d41f0d8de8957");
        kat("password", "salt", 4096, 20, "4b007901b765489abead49d926f721d065a429c1");
        kat("passwordPASSWORDpassword", "saltSALTsaltSALTsaltSALTsaltSALTsalt", 4096, 25,
                "3d2eec4fe41c849b80c8d83662c0e44a8b291a964cf2f07038");

        // Today's choice: HMAC-SHA256, random 16-byte salt, 600,000 iterations,
        // 32-byte output (exactly one SHA-256 block of work).
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        char[] password = {'c', 'o', 'r', 'r', 'e', 'c', 't', ' ', 'h', 'o', 'r', 's', 'e'};
        PBEKeySpec spec = new PBEKeySpec(password, salt, 600_000, 256);
        Arrays.fill(password, '\0'); // PBEKeySpec keeps its own copy
        byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        spec.clearPassword();
        System.out.println("PBKDF2WithHmacSHA256: " + key.length * 8 + "-bit key from a "
                + salt.length * 8 + "-bit salt and 600000 iterations");

        // Same password, different salt -> unrelated key. (A lower iteration
        // count only keeps this demo fast; it is not a recommendation.)
        byte[] salt2 = salt.clone();
        salt2[0] ^= 1;
        byte[] k1 = derive("correct horse", salt, 10_000, 256);
        System.out.println("different salt gives a different key: "
                + !MessageDigest.isEqual(k1, derive("correct horse", salt2, 10_000, 256)));
        System.out.println("same salt gives the same key: "
                + MessageDigest.isEqual(k1, derive("correct horse", salt, 10_000, 256)));
        System.out.println("iteration count is part of the result: "
                + !MessageDigest.isEqual(k1, derive("correct horse", salt, 10_001, 256)));
    }

    static byte[] derive(String password, byte[] salt, int iterations, int bits) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, bits);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    static void kat(String password, String salt, int c, int dkLen, String expectedHex) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt.getBytes(StandardCharsets.US_ASCII), c, dkLen * 8);
        byte[] dk = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
        spec.clearPassword();
        String hex = HexFormat.of().formatHex(dk);
        System.out.printf("P=%-24s c=%-4d dkLen=%d -> %s... %s%n", password, c, dkLen, hex.substring(0, 12),
                hex.equals(expectedHex) ? "OK" : "MISMATCH");
    }
}

/* Output:
P=password                 c=1    dkLen=20 -> 0c60c80f961f... OK
P=password                 c=2    dkLen=20 -> ea6c014dc72d... OK
P=password                 c=4096 dkLen=20 -> 4b007901b765... OK
P=passwordPASSWORDpassword c=4096 dkLen=25 -> 3d2eec4fe41c... OK
PBKDF2WithHmacSHA256: 256-bit key from a 128-bit salt and 600000 iterations
different salt gives a different key: true
same salt gives the same key: true
iteration count is part of the result: true
*/
