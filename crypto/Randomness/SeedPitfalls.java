// Entropy and seeding pitfalls: clock seeds, small seed spaces, cloned state
//
// A cryptographic generator is only as unpredictable as its seed. Three ways
// real systems got this wrong, each reproduced below without real attacks:
//  1. Seeding from the clock. Even a "SecureRandom" (SHA1PRNG seeded with
//     setSeed before first use) becomes a deterministic function of the seed,
//     and a start time known to within seconds (logs, uptime, file dates)
//     leaves a few thousand candidates to try.
//  2. Small seed spaces: a 32-bit seed (a PID, a hashCode, a timestamp in
//     seconds) is at most 4 billion possibilities, which is nothing offline.
//  3. Cloned state: a VM snapshot restored twice, a forked process or a
//     serialized generator gives two copies that emit the SAME "random"
//     bytes unless the generator reseeds. Simulated here with serialization:
//     SHA1PRNG copies its state verbatim; DRBG reseeds when deserialized.
// The rules: use new SecureRandom() (or getInstance("DRBG")), never seed it
// yourself from predictable data, reseed after snapshot/restore, and leave
// entropy to the OS. (The old "-Djava.security.egd=file:/dev/./urandom"
// trick worked around blocking reads of /dev/random in very old JDKs; on
// current JDKs and Linux kernels it is cargo cult.)
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;

public class SeedPitfalls {
    // NONCOMPLIANT: a token service that seeds a SHA1PRNG from its start time
    static final class TokenService {
        private final SecureRandom rng;

        TokenService(long startMillis) throws Exception {
            rng = SecureRandom.getInstance("SHA1PRNG");
            rng.setSeed(startMillis); // before first use: REPLACES self-seeding
        }

        String next() {
            byte[] token = new byte[16];
            rng.nextBytes(token);
            return HexFormat.of().formatHex(token);
        }
    }

    static SecureRandom cloneBySerialization(SecureRandom r) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(r);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (SecureRandom) in.readObject();
        }
    }

    public static void main(String[] args) throws Exception {
        // 1. The server started at some millisecond the attacker knows only roughly
        TokenService service = new TokenService(Instant.parse("2025-07-11T09:00:07.314Z").toEpochMilli());
        String attackersOwnToken = service.next();  // the attacker signs up and receives a token
        String victimsResetToken = service.next();  // then the victim requests a password reset
        long logLine = Instant.parse("2025-07-11T09:00:00Z").toEpochMilli(); // "service started" around 09:00
        long tried = 0;
        TokenService replica = null;
        for (long guess = logLine; guess < logLine + 20_000; guess++) { // a 20-second window
            tried++;
            TokenService candidate = new TokenService(guess);
            if (candidate.next().equals(attackersOwnToken)) { replica = candidate; break; }
        }
        System.out.println("seed recovered from the attacker's own token: " + (replica != null) + " after " + tried + " guesses");
        System.out.println("victim's reset token predicted:              "
                + (replica != null && replica.next().equals(victimsResetToken)));

        // 2. Seed space sizes, in bits
        System.out.println("seed space: PID ~22 bits, seconds since 1970 ~31 bits, millis in a day ~27 bits, OS entropy 256+ bits");

        // 3. Cloned generator state
        for (String alg : new String[] {"SHA1PRNG", "DRBG"}) {
            SecureRandom original = SecureRandom.getInstance(alg);
            original.nextInt(); // in use before the "snapshot"
            SecureRandom vmA = cloneBySerialization(original);
            SecureRandom vmB = cloneBySerialization(original);
            byte[] a = new byte[32], b = new byte[32];
            vmA.nextBytes(a);
            vmB.nextBytes(b);
            System.out.printf("%-8s two restored copies produce identical bytes: %s%n", alg, Arrays.equals(a, b));
        }

        // The compliant default: never seeded by us, and copies cannot be predicted from one another
        SecureRandom good = new SecureRandom();
        byte[] t1 = new byte[16], t2 = new byte[16];
        good.nextBytes(t1);
        good.nextBytes(t2);
        System.out.println("new SecureRandom(), two tokens differ: " + !Arrays.equals(t1, t2));
    }
}

/* Output:
seed recovered from the attacker's own token: true after 7315 guesses
victim's reset token predicted:              true
seed space: PID ~22 bits, seconds since 1970 ~31 bits, millis in a day ~27 bits, OS entropy 256+ bits
SHA1PRNG two restored copies produce identical bytes: true
DRBG     two restored copies produce identical bytes: false
new SecureRandom(), two tokens differ: true
*/
