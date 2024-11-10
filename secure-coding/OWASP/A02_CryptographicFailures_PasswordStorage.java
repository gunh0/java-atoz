// OWASP Top 10 2021: A02:2021 Cryptographic Failures, password storage (CWE-916, CWE-759)
// https://owasp.org/Top10/A02_2021-Cryptographic_Failures/
//
// A password database will leak sooner or later, so what matters is how
// expensive each stored hash is to crack. A plain fast hash (MD5, SHA-1,
// SHA-256) costs an attacker nanoseconds per guess on a GPU, and without
// a salt, equal passwords have equal hashes and precomputed tables work.
// Use a deliberately slow, salted password hashing function. Of the
// standard ones, the JDK ships PBKDF2 (PBKDF2WithHmacSHA256); OWASP's
// Password Storage Cheat Sheet suggests at least 600,000 iterations for
// it, a unique random salt of 16+ bytes per password, and storing the
// algorithm and parameters with the hash so they can be raised later.
// (Argon2id, scrypt and bcrypt are preferred where a library is available.)
// Compare hashes in constant time.
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class A02_CryptographicFailures_PasswordStorage {
    static final int ITERATIONS = 600_000;
    static final int SALT_BYTES = 16;
    static final int HASH_BITS = 256;
    static final SecureRandom RANDOM = new SecureRandom();

    public static void main(String[] args) throws Exception {
        // Noncompliant: unsalted SHA-256
        String a = sha256Hex("Summer2024!"), b = sha256Hex("Summer2024!");
        System.out.println("noncompliant: alice and bob share a password, hashes equal: " + a.equals(b));
        System.out.println("noncompliant: the hash of \"password\" is the same everywhere: "
                + sha256Hex("password").substring(0, 16) + "...");

        // Compliant: PBKDF2-HMAC-SHA256, random salt, parameters stored with the hash
        String storedAlice = hash("Summer2024!".toCharArray());
        String storedBob = hash("Summer2024!".toCharArray());
        String[] parts = storedAlice.split("\\$");
        System.out.println("compliant: format " + parts[0] + "$" + parts[1] + "$<salt " + Base64.getDecoder().decode(parts[2]).length
                + " bytes>$<hash " + Base64.getDecoder().decode(parts[3]).length + " bytes>");
        System.out.println("compliant: alice and bob share a password, hashes equal: " + storedAlice.equals(storedBob));
        System.out.println("compliant: verify correct password: " + verify("Summer2024!".toCharArray(), storedAlice));
        System.out.println("compliant: verify wrong password:   " + verify("Summer2025!".toCharArray(), storedAlice));

        // Old records with weaker parameters are re-hashed after a successful login
        String legacy = hashWith("Summer2024!".toCharArray(), newSalt(), 10_000);
        System.out.println("legacy record needs rehash: " + needsRehash(legacy) + ", current record: " + needsRehash(storedAlice));
    }

    static String sha256Hex(String s) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    static byte[] newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return salt;
    }

    static String hash(char[] password) throws Exception {
        return hashWith(password, newSalt(), ITERATIONS);
    }

    static String hashWith(char[] password, byte[] salt, int iterations) throws Exception {
        byte[] dk = pbkdf2(password, salt, iterations);
        Base64.Encoder b64 = Base64.getEncoder().withoutPadding();
        return "pbkdf2-sha256$" + iterations + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(dk);
    }

    static boolean verify(char[] password, String stored) throws Exception {
        String[] p = stored.split("\\$");
        if (p.length != 4 || !p[0].equals("pbkdf2-sha256")) return false;
        int iterations = Integer.parseInt(p[1]);
        byte[] salt = Base64.getDecoder().decode(p[2]);
        byte[] expected = Base64.getDecoder().decode(p[3]);
        byte[] actual = pbkdf2(password, salt, iterations);
        return MessageDigest.isEqual(expected, actual); // constant time
    }

    static boolean needsRehash(String stored) {
        return Integer.parseInt(stored.split("\\$")[1]) < ITERATIONS;
    }

    static byte[] pbkdf2(char[] password, byte[] salt, int iterations)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword(); // wipe the copy held by the spec
        }
    }
}

/* Output:
noncompliant: alice and bob share a password, hashes equal: true
noncompliant: the hash of "password" is the same everywhere: 5e884898da280471...
compliant: format pbkdf2-sha256$600000$<salt 16 bytes>$<hash 32 bytes>
compliant: alice and bob share a password, hashes equal: false
compliant: verify correct password: true
compliant: verify wrong password:   false
legacy record needs rehash: true, current record: false
*/
