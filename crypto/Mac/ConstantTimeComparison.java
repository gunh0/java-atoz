// Constant-time comparison: how MessageDigest.isEqual got there
//
// Misuse/MacComparison.java shows why an early-exit comparison of MACs leaks
// the correct prefix. This file looks at the fix itself. MessageDigest.isEqual
// was once an ordinary loop that returned at the first differing byte; the
// timing leak was reported in 2009 (CVE-2009-3875) and fixed in Java 6
// update 17 by OR-ing the XOR of every byte pair into an accumulator and
// testing it once at the end. Its length handling was reworked later: today
// it still returns false for different lengths, but it walks the whole FIRST
// argument either way, so its running time depends only on digesta's length,
// never on the contents or length of digestb. Pass the expected value first
// and the attacker-controlled value second. Java gives no hard guarantee that
// the JIT keeps such code branch-free, so the belt-and-braces alternative is
// "double HMAC": MAC both values under a fresh random key and compare those.
// Byte work is counted below instead of measuring time, so output is stable.
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class ConstantTimeComparison {
    static int work; // bytes examined: stands in for time

    // Pre-2009 isEqual, and every hand-written loop that "returns early"
    static boolean earlyExit(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            work++;
            if (a[i] != b[i]) return false;
        }
        return true;
    }

    // The 6u17 fix: accumulate differences, decide once (length checked first)
    static boolean accumulate(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            work++;
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    // The shape of today's isEqual: always walk all of a, fold the length difference in
    static boolean lengthIndependent(byte[] a, byte[] b) {
        if (b.length == 0) return a.length == 0;
        int result = a.length - b.length;
        for (int i = 0; i < a.length; i++) {
            work++;
            int indexB = ((i - b.length) >>> 31) * i; // i while i < b.length, else 0, without a branch
            result |= a[i] ^ b[indexB];
        }
        return result == 0;
    }

    interface Comparator { boolean eq(byte[] a, byte[] b); }

    static void probe(String name, Comparator c, byte[] expected, byte[][] guesses, String[] labels) {
        System.out.println(name);
        for (int i = 0; i < guesses.length; i++) {
            work = 0;
            boolean r = c.eq(expected, guesses[i]);
            System.out.printf("  %-26s -> %-5s work=%d%n", labels[i], r, work);
        }
    }

    public static void main(String[] args) throws Exception {
        HexFormat hex = HexFormat.of();
        byte[] expected = hex.parseHex("8bb8c7f2d5a1e0937c64b2f0aa5d19e4"); // a 16-byte tag
        byte[] wrongFirst = expected.clone();
        wrongFirst[0] ^= 1;
        byte[] wrongLast = expected.clone();
        wrongLast[15] ^= 1;
        byte[] shortGuess = hex.parseHex("8bb8c7f2");
        byte[] longGuess = hex.parseHex("8bb8c7f2d5a1e0937c64b2f0aa5d19e400000000000000000000");
        byte[][] guesses = {wrongFirst, wrongLast, expected.clone(), shortGuess, longGuess};
        String[] labels = {"wrong first byte", "wrong last byte", "correct", "4-byte guess", "26-byte guess"};

        probe("early exit: work reveals the correct prefix", ConstantTimeComparison::earlyExit, expected, guesses, labels);
        probe("accumulate (6u17): same work for any same-length guess", ConstantTimeComparison::accumulate, expected, guesses, labels);
        probe("length-independent (today's isEqual): work = expected.length", ConstantTimeComparison::lengthIndependent, expected, guesses, labels);

        // The real method agrees with the model on results, including the edge cases
        byte[][][] cases = {{expected, wrongLast}, {expected, expected.clone()}, {expected, shortGuess},
                {expected, longGuess}, {new byte[0], new byte[0]}, {expected, new byte[0]}, {expected, null}};
        boolean agree = true;
        for (byte[][] pair : cases) {
            boolean model = pair[1] != null && lengthIndependent(pair[0], pair[1]);
            agree &= model == MessageDigest.isEqual(pair[0], pair[1]);
        }
        System.out.println("MessageDigest.isEqual agrees with the model: " + agree);

        // Double HMAC: compare MACs of both values under a one-off random key.
        // Even an early-exit comparison then leaks nothing the attacker can steer.
        byte[] blindKey = new byte[32];
        new SecureRandom().nextBytes(blindKey);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(blindKey, "HmacSHA256"));
        byte[] e = mac.doFinal(expected);
        System.out.println("double HMAC, correct tag:  " + MessageDigest.isEqual(e, mac.doFinal(expected.clone())));
        System.out.println("double HMAC, wrong last:   " + MessageDigest.isEqual(e, mac.doFinal(wrongLast)));

        // Compare bytes, not hex strings: "8BB8..." vs "8bb8..." are the same tag
        String upper = hex.withUpperCase().formatHex(expected);
        System.out.println("hex strings equal:          " + upper.equals(hex.formatHex(expected)));
        System.out.println("decoded bytes equal:        "
                + MessageDigest.isEqual(expected, hex.parseHex(upper)));
    }
}

/* Output:
early exit: work reveals the correct prefix
  wrong first byte           -> false work=1
  wrong last byte            -> false work=16
  correct                    -> true  work=16
  4-byte guess               -> false work=0
  26-byte guess              -> false work=0
accumulate (6u17): same work for any same-length guess
  wrong first byte           -> false work=16
  wrong last byte            -> false work=16
  correct                    -> true  work=16
  4-byte guess               -> false work=0
  26-byte guess              -> false work=0
length-independent (today's isEqual): work = expected.length
  wrong first byte           -> false work=16
  wrong last byte            -> false work=16
  correct                    -> true  work=16
  4-byte guess               -> false work=16
  26-byte guess              -> false work=16
MessageDigest.isEqual agrees with the model: true
double HMAC, correct tag:  true
double HMAC, wrong last:   false
hex strings equal:          false
decoded bytes equal:        true
*/
