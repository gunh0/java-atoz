// TOTP: time-based one-time passwords from scratch (RFC 6238)
//
// TOTP is HOTP (RFC 4226, Mac/Hotp.java) with the counter replaced by the
// number of 30-second steps since the Unix epoch: T = floor((now - T0) / X).
// RFC 6238 also allows HMAC-SHA256 and HMAC-SHA512 (authenticator apps
// overwhelmingly use SHA-1, which is fine here: HMAC-SHA1 is not broken the
// way SHA-1 collisions are). Points a server must get right: allow a small
// clock-skew window (one step either side is typical), remember the last
// accepted step per user so a code cannot be replayed within its lifetime,
// throttle guesses, and take the time from an injectable Clock so the logic
// is testable. The program checks every test vector of RFC 6238 Appendix B,
// whose keys are the ASCII digits "1234567890" repeated to the hash length.
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class Totp {
    static final long STEP_SECONDS = 30;

    static String totp(String hmacAlg, byte[] key, long step, int digits) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(hmacAlg);
        mac.init(new SecretKeySpec(key, hmacAlg));
        byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
        int offset = h[h.length - 1] & 0x0f;
        int bin = ((h[offset] & 0x7f) << 24) | ((h[offset + 1] & 0xff) << 16)
                | ((h[offset + 2] & 0xff) << 8) | (h[offset + 3] & 0xff);
        return String.format("%0" + digits + "d", bin % (int) Math.pow(10, digits));
    }

    static byte[] seed(int length) {
        byte[] digits = "1234567890".getBytes(StandardCharsets.US_ASCII);
        byte[] k = new byte[length];
        for (int i = 0; i < length; i++) k[i] = digits[i % 10];
        return k;
    }

    // Server: skew window of one step and replay protection via the last accepted step
    static final class TotpVerifier {
        private final byte[] key;
        private final Clock clock;
        private long lastAcceptedStep = -1;

        TotpVerifier(byte[] key, Clock clock) {
            this.key = key.clone();
            this.clock = clock;
        }

        boolean verify(String code) throws GeneralSecurityException {
            long now = clock.instant().getEpochSecond() / STEP_SECONDS;
            for (long step = now - 1; step <= now + 1; step++) {
                if (step <= lastAcceptedStep) continue; // already used (or older)
                byte[] expected = totp("HmacSHA1", key, step, 6).getBytes(StandardCharsets.US_ASCII);
                if (MessageDigest.isEqual(expected, code.getBytes(StandardCharsets.US_ASCII))) {
                    lastAcceptedStep = step;
                    return true;
                }
            }
            return false;
        }
    }

    public static void main(String[] args) throws Exception {
        long[] times = {59L, 1111111109L, 1111111111L, 1234567890L, 2000000000L, 20000000000L};
        String[][] expected = {
                {"94287082", "46119246", "90693936"},
                {"07081804", "68084774", "25091201"},
                {"14050471", "67062674", "99943326"},
                {"89005924", "91819424", "93441116"},
                {"69279037", "90698825", "38618901"},
                {"65353130", "77737706", "47863826"}};
        String[] algs = {"HmacSHA1", "HmacSHA256", "HmacSHA512"};
        byte[][] keys = {seed(20), seed(32), seed(64)};

        boolean allMatch = true;
        for (int i = 0; i < times.length; i++) {
            long step = times[i] / STEP_SECONDS;
            StringBuilder line = new StringBuilder(String.format("T=%-11d step=%016X", times[i], step));
            for (int a = 0; a < algs.length; a++) {
                String code = totp(algs[a], keys[a], step, 8);
                allMatch &= code.equals(expected[i][a]);
                line.append("  ").append(code);
            }
            System.out.println(line);
        }
        System.out.println("RFC 6238 vectors (SHA1, SHA256, SHA512) match: " + allMatch);

        // A server whose clock is fixed at T = 1111111111 (step 37037037)
        byte[] key = seed(20);
        Clock clock = Clock.fixed(Instant.ofEpochSecond(1111111111L), ZoneOffset.UTC);
        TotpVerifier server = new TotpVerifier(key, clock);
        String current = totp("HmacSHA1", key, 37037037, 6);
        String previous = totp("HmacSHA1", key, 37037036, 6);
        String twoStepsOld = totp("HmacSHA1", key, 37037035, 6);
        System.out.println("code from two steps ago:       " + server.verify(twoStepsOld));
        System.out.println("code from the previous step:   " + server.verify(previous));
        System.out.println("current code:                  " + server.verify(current));
        System.out.println("current code replayed:         " + server.verify(current));
        System.out.println("previous code after current:   " + server.verify(previous));
    }
}

/* Output:
T=59          step=0000000000000001  94287082  46119246  90693936
T=1111111109  step=00000000023523EC  07081804  68084774  25091201
T=1111111111  step=00000000023523ED  14050471  67062674  99943326
T=1234567890  step=000000000273EF07  89005924  91819424  93441116
T=2000000000  step=0000000003F940AA  69279037  90698825  38618901
T=20000000000 step=0000000027BC86AA  65353130  77737706  47863826
RFC 6238 vectors (SHA1, SHA256, SHA512) match: true
code from two steps ago:       false
code from the previous step:   true
current code:                  true
current code replayed:         false
previous code after current:   false
*/
