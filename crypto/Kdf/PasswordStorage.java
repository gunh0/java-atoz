// Storing password hashes: salt, work factor and parameters in one string
//
// A login database must let you check a password without being able to
// recover it. Store, per user, a self-describing record of algorithm,
// iteration count, random salt and derived hash, e.g.
//   pbkdf2-sha256$600000$<salt b64>$<hash b64>
// so that verification re-derives with the STORED parameters, and you can
// raise the iteration count later: on a successful login, if the record's
// cost is below today's policy, re-hash the password you now hold and
// replace the record. Compare in constant time, and give the same answer
// for "no such user" as for "wrong password". Plain SHA-256(password),
// with or without a global "pepper" string, falls to GPU dictionaries in
// minutes.
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class PasswordStorage {
    static final int CURRENT_ITERATIONS = 600_000;
    static final SecureRandom RANDOM = new SecureRandom();

    static String hash(char[] password, int iterations) throws Exception {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] dk = pbkdf2(password, salt, iterations);
        Base64.Encoder b64 = Base64.getEncoder().withoutPadding();
        return "pbkdf2-sha256$" + iterations + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(dk);
    }

    static boolean verify(char[] password, String stored) throws Exception {
        String[] f = stored.split("\\$");
        if (f.length != 4 || !f[0].equals("pbkdf2-sha256")) return false;
        int iterations = Integer.parseInt(f[1]);
        byte[] salt = Base64.getDecoder().decode(f[2]);
        byte[] expected = Base64.getDecoder().decode(f[3]);
        return MessageDigest.isEqual(pbkdf2(password, salt, iterations), expected);
    }

    static boolean needsRehash(String stored) {
        return Integer.parseInt(stored.split("\\$")[1]) < CURRENT_ITERATIONS;
    }

    static byte[] pbkdf2(char[] password, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    public static void main(String[] args) throws Exception {
        char[] pw = "Tr0ub4dor&3".toCharArray();

        String record = hash(pw, CURRENT_ITERATIONS);
        String[] fields = record.split("\\$");
        System.out.println("record fields: " + fields[0] + " / " + fields[1] + " / salt " + fields[2].length()
                + " chars / hash " + fields[3].length() + " chars");
        System.out.println("correct password: " + verify(pw, record));
        System.out.println("wrong password:   " + verify("Tr0ub4dor&4".toCharArray(), record));

        // Two users with the same password get unrelated records
        System.out.println("same password, same record: " + hash(pw, 1_000).equals(hash(pw, 1_000)));

        // An old record with a lower cost still verifies, and is flagged
        String legacy = hash(pw, 10_000);
        System.out.println("legacy record verifies: " + verify(pw, legacy) + ", needs rehash: " + needsRehash(legacy));
        if (verify(pw, legacy) && needsRehash(legacy)) {
            legacy = hash(pw, CURRENT_ITERATIONS); // we hold the plaintext only now, at login
        }
        System.out.println("after login upgrade, needs rehash: " + needsRehash(legacy));

        Arrays.fill(pw, '\0');
    }
}

/* Output:
record fields: pbkdf2-sha256 / 600000 / salt 22 chars / hash 43 chars
correct password: true
wrong password:   false
same password, same record: false
legacy record verifies: true, needs rehash: true
after login upgrade, needs rehash: false
*/
