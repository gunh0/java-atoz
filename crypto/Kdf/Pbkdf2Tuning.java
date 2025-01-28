// No Argon2 or scrypt in the JDK: tuning PBKDF2 instead
//
// The modern password hashes are memory-hard: Argon2id (RFC 9106, winner of
// the Password Hashing Competition) and scrypt (RFC 7914) make every guess
// need megabytes of RAM, which is what slows down GPU and ASIC crackers.
// Neither is in the JDK; you need a library (Bouncy Castle, or a binding to
// libargon2). If you must stay with the JDK, or with FIPS 140, PBKDF2 is what
// you have, and then its parameters are the whole defence:
//  - the PRF: HMAC-SHA512 is slower on GPUs (64-bit arithmetic) than SHA-256,
//    which is why OWASP's 2023 floors are 210,000 iterations for SHA-512,
//    600,000 for SHA-256 and 1,300,000 for SHA-1;
//  - the iteration count: calibrate it on your own hardware to a latency
//    budget, never below the floor, and store it with each hash;
//  - the input: PBEKeySpec encodes chars as UTF-8, so normalize first (one
//    "é" can be one code point or two), and know that HMAC pre-hashes keys
//    longer than its block size, so a long password and its hash are aliases.
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Security;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class Pbkdf2Tuning {
    static final Map<String, Integer> OWASP_FLOOR = Map.of(
            "PBKDF2WithHmacSHA1", 1_300_000, "PBKDF2WithHmacSHA256", 600_000, "PBKDF2WithHmacSHA512", 210_000);

    static byte[] jdkPbkdf2(String alg, char[] password, byte[] salt, int iterations, int bits) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, bits);
        try {
            return SecretKeyFactory.getInstance(alg).generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    // PBKDF2 (RFC 8018) for one output block, over Mac: the password is the HMAC key
    static byte[] pbkdf2Block1(byte[] passwordBytes, byte[] salt, int iterations) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(passwordBytes, "HmacSHA256"));
        mac.update(salt);
        byte[] u = mac.doFinal(new byte[] {0, 0, 0, 1}); // INT(1): first block
        byte[] t = u.clone();
        for (int i = 1; i < iterations; i++) {
            u = mac.doFinal(u);
            for (int j = 0; j < t.length; j++) t[j] ^= u[j];
        }
        return t;
    }

    // Pick an iteration count for a time budget, but never go below the floor
    static int calibrate(String alg, long budgetNanos) throws Exception {
        int probe = 20_000;
        byte[] salt = new byte[16];
        jdkPbkdf2(alg, "warm-up".toCharArray(), salt, probe, 256); // let the JIT compile it
        long t0 = System.nanoTime();
        jdkPbkdf2(alg, "calibrate".toCharArray(), salt, probe, 256);
        long elapsed = Math.max(1, System.nanoTime() - t0);
        long scaled = probe * budgetNanos / elapsed;
        return (int) Math.max(OWASP_FLOOR.get(alg), Math.min(scaled, 10_000_000));
    }

    public static void main(String[] args) throws Exception {
        TreeSet<String> kdfs = new TreeSet<>();
        for (String s : Security.getAlgorithms("SecretKeyFactory")) if (s.startsWith("PBKDF2")) kdfs.add(s);
        System.out.println("PBKDF2 variants: " + kdfs);
        for (String memoryHard : new String[] {"SCRYPT", "ARGON2ID", "ARGON2"}) {
            System.out.println(memoryHard + " in the JDK: " + Security.getAlgorithms("SecretKeyFactory").contains(memoryHard));
        }

        // Calibration: the chosen value depends on the machine, so print only the verdict
        int chosen = calibrate("PBKDF2WithHmacSHA256", 250_000_000L);
        System.out.println("calibrated SHA-256 iterations >= OWASP floor: " + (chosen >= OWASP_FLOOR.get("PBKDF2WithHmacSHA256")));
        new TreeSet<>(OWASP_FLOOR.keySet()).forEach(k -> System.out.printf(Locale.ROOT, "  floor %-21s %,d%n", k, OWASP_FLOOR.get(k)));

        // The Mac-based version agrees with SecretKeyFactory (UTF-8 password bytes)
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        char[] pw = "correct horse battery staple".toCharArray();
        byte[] viaJdk = jdkPbkdf2("PBKDF2WithHmacSHA256", pw, salt, 1000, 256);
        byte[] viaMac = pbkdf2Block1(new String(pw).getBytes(StandardCharsets.UTF_8), salt, 1000);
        System.out.println("Mac-based PBKDF2 == SecretKeyFactory: " + MessageDigest.isEqual(viaJdk, viaMac));

        // Long passwords: HMAC replaces a key longer than 64 bytes by SHA-256(key)
        byte[] longPw = "a passphrase that is definitely longer than sixty-four bytes in UTF-8!".getBytes(StandardCharsets.UTF_8);
        byte[] alias = MessageDigest.getInstance("SHA-256").digest(longPw);
        System.out.println("password length " + longPw.length + " bytes; alias is its 32-byte SHA-256");
        System.out.println("PBKDF2(password) == PBKDF2(SHA-256(password)): "
                + MessageDigest.isEqual(pbkdf2Block1(longPw, salt, 1000), pbkdf2Block1(alias, salt, 1000)));

        // Unicode normalization: same text on screen, different bytes, different hash
        String composed = "café";       // é as one code point
        String decomposed = "café";    // e + combining acute accent
        byte[] h1 = jdkPbkdf2("PBKDF2WithHmacSHA256", composed.toCharArray(), salt, 1000, 256);
        byte[] h2 = jdkPbkdf2("PBKDF2WithHmacSHA256", decomposed.toCharArray(), salt, 1000, 256);
        byte[] n1 = jdkPbkdf2("PBKDF2WithHmacSHA256", Normalizer.normalize(composed, Normalizer.Form.NFKC).toCharArray(), salt, 1000, 256);
        byte[] n2 = jdkPbkdf2("PBKDF2WithHmacSHA256", Normalizer.normalize(decomposed, Normalizer.Form.NFKC).toCharArray(), salt, 1000, 256);
        System.out.println("\"café\" composed vs decomposed, raw:       " + MessageDigest.isEqual(h1, h2));
        System.out.println("\"café\" composed vs decomposed, after NFKC: " + MessageDigest.isEqual(n1, n2));
    }
}

/* Output:
PBKDF2 variants: [PBKDF2WITHHMACSHA1, PBKDF2WITHHMACSHA224, PBKDF2WITHHMACSHA256, PBKDF2WITHHMACSHA384, PBKDF2WITHHMACSHA512, PBKDF2WITHHMACSHA512/224, PBKDF2WITHHMACSHA512/256]
SCRYPT in the JDK: false
ARGON2ID in the JDK: false
ARGON2 in the JDK: false
calibrated SHA-256 iterations >= OWASP floor: true
  floor PBKDF2WithHmacSHA1    1,300,000
  floor PBKDF2WithHmacSHA256  600,000
  floor PBKDF2WithHmacSHA512  210,000
Mac-based PBKDF2 == SecretKeyFactory: true
password length 70 bytes; alias is its 32-byte SHA-256
PBKDF2(password) == PBKDF2(SHA-256(password)): true
"café" composed vs decomposed, raw:       false
"café" composed vs decomposed, after NFKC: true
*/
