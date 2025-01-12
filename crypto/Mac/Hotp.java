// HOTP: HMAC-based one-time passwords from scratch (RFC 4226)
//
// An HOTP code is HMAC-SHA1(secret, 8-byte big-endian counter), shrunk to a
// few decimal digits by "dynamic truncation": the low 4 bits of the last
// byte pick an offset, the 31 bits at that offset (top bit masked so the
// value is positive on every platform) are taken mod 10^digits. Client and
// server each keep the counter; the server accepts a code within a small
// look-ahead window (the user may have pressed the button without logging
// in) and then moves its counter PAST the accepted value, so every code is
// single-use. Six digits mean a 1-in-a-million guess, so the server must
// also throttle failed attempts (RFC 4226 section 7.3). The shared secret is
// a key: store it like one (encrypted at rest), not like a password hash.
// The program checks the RFC 4226 Appendix D test vectors.
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class Hotp {
    static byte[] hmacSha1(byte[] key, long counter) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key, "HmacSHA1"));
        return mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
    }

    static int truncate(byte[] hmac) {
        int offset = hmac[hmac.length - 1] & 0x0f;
        return ((hmac[offset] & 0x7f) << 24)
                | ((hmac[offset + 1] & 0xff) << 16)
                | ((hmac[offset + 2] & 0xff) << 8)
                | (hmac[offset + 3] & 0xff);
    }

    static String hotp(byte[] key, long counter, int digits) throws GeneralSecurityException {
        int code = truncate(hmacSha1(key, counter)) % (int) Math.pow(10, digits);
        return String.format("%0" + digits + "d", code); // keep leading zeros
    }

    // Server side: counter state plus a look-ahead window
    static final class HotpVerifier {
        private final byte[] key;
        private long counter;
        private final int window;

        HotpVerifier(byte[] key, long counter, int window) {
            this.key = key.clone();
            this.counter = counter;
            this.window = window;
        }

        boolean verify(String code) throws GeneralSecurityException {
            for (long c = counter; c <= counter + window; c++) {
                // Same length and constant-time comparison, as for any MAC
                if (MessageDigest.isEqual(hotp(key, c, 6).getBytes(StandardCharsets.US_ASCII),
                        code.getBytes(StandardCharsets.US_ASCII))) {
                    counter = c + 1; // resynchronise and burn every code up to c
                    return true;
                }
            }
            return false;
        }
    }

    public static void main(String[] args) throws Exception {
        byte[] secret = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        String[] expected = {"755224", "287082", "359152", "969429", "338314",
                             "254676", "287922", "162583", "399871", "520489"};

        // Intermediate values from RFC 4226 Appendix D for count 0
        byte[] h0 = hmacSha1(secret, 0);
        System.out.println("HMAC(count=0)      " + HexFormat.of().formatHex(h0));
        System.out.println("offset             " + (h0[19] & 0x0f));
        System.out.println("truncated (31 bit) " + truncate(h0));

        boolean allMatch = true;
        for (int count = 0; count < expected.length; count++) {
            String code = hotp(secret, count, 6);
            boolean ok = code.equals(expected[count]);
            allMatch &= ok;
            System.out.println("count " + count + " -> " + code + (ok ? "  ok" : "  MISMATCH"));
        }
        System.out.println("RFC 4226 vectors match: " + allMatch);

        // The look-ahead window and single use
        HotpVerifier server = new HotpVerifier(secret, 0, 3);
        System.out.println("accept code for count 2 (within window): " + server.verify(expected[2]));
        System.out.println("replay the same code:                    " + server.verify(expected[2]));
        System.out.println("older code for count 1:                  " + server.verify(expected[1]));
        System.out.println("code for count 9 (beyond window):        " + server.verify(expected[9]));
        System.out.println("next code, count 3:                      " + server.verify(expected[3]));
    }
}

/* Output:
HMAC(count=0)      cc93cf18508d94934c64b65d8ba7667fb7cde4b0
offset             0
truncated (31 bit) 1284755224
count 0 -> 755224  ok
count 1 -> 287082  ok
count 2 -> 359152  ok
count 3 -> 969429  ok
count 4 -> 338314  ok
count 5 -> 254676  ok
count 6 -> 287922  ok
count 7 -> 162583  ok
count 8 -> 399871  ok
count 9 -> 520489  ok
RFC 4226 vectors match: true
accept code for count 2 (within window): true
replay the same code:                    false
older code for count 1:                  false
code for count 9 (beyond window):        false
next code, count 3:                      true
*/
