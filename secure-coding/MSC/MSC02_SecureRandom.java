// MSC02-J. Generate strong random numbers
// https://wiki.sei.cmu.edu/confluence/display/java/MSC02-J.+Generate+strong+random+numbers
//
// java.util.Random is a 48-bit linear congruential generator: fast and fine
// for simulations, but every output reveals 32 of its 48 state bits. Anyone
// who sees two consecutive nextInt() values can brute-force the remaining 16
// bits (65,536 guesses) and predict every future value. Session IDs, reset
// tokens, nonces, salts and keys must come from java.security.SecureRandom,
// a cryptographically strong generator seeded from the operating system.
// Do not seed SecureRandom yourself with predictable data, and do not share
// one java.util.Random "for security" by seeding it with the clock.
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Random;

public class MSC02_SecureRandom {
    public static void main(String[] args) {
        // Noncompliant: a server issues "random" tokens with java.util.Random.
        // A fixed seed keeps this demo deterministic; a clock-based seed would
        // not help, because the attack below does not need the seed at all.
        Random weak = new Random(20211120L);
        int token1 = weak.nextInt();
        int token2 = weak.nextInt();
        int token3 = weak.nextInt(); // the victim's token, never shown to the attacker

        // The attacker observed token1 and token2 (e.g. their own two sessions)
        Integer predicted = predictNext(token1, token2);
        System.out.println("noncompliant: attacker saw two tokens");
        System.out.println("noncompliant: predicted next token correctly: " + (predicted != null && predicted == token3));

        // Compliant: SecureRandom, and enough bits (128) for an unguessable token
        SecureRandom strong = new SecureRandom();
        byte[] bytes = new byte[16];
        strong.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        System.out.println("compliant: token has " + token.length() + " hex chars (" + bytes.length * 8 + " bits)");

        // The same attack fails: SecureRandom output is not an LCG state
        int s1 = strong.nextInt();
        int s2 = strong.nextInt();
        int s3 = strong.nextInt();
        Integer guess = predictNext(s1, s2);
        System.out.println("compliant: attack predicted the next value: " + (guess != null && guess == s3));
    }

    // Recover java.util.Random's internal state from two consecutive nextInt()
    // outputs and return the third. Uses only the public algorithm documented
    // in the Random class: seed = (seed * 0x5DEECE66DL + 0xBL) mod 2^48, and
    // nextInt() returns the top 32 bits of the new seed.
    static Integer predictNext(int first, int second) {
        final long multiplier = 0x5DEECE66DL;
        final long addend = 0xBL;
        final long mask = (1L << 48) - 1;
        for (long low = 0; low < (1 << 16); low++) {
            long seed = (((long) first & 0xFFFF_FFFFL) << 16) | low; // guess the hidden 16 bits
            long next = (seed * multiplier + addend) & mask;
            if ((int) (next >>> 16) == second) {
                long after = (next * multiplier + addend) & mask;
                return (int) (after >>> 16);
            }
        }
        return null;
    }
}

/* Output:
noncompliant: attacker saw two tokens
noncompliant: predicted next token correctly: true
compliant: token has 32 hex chars (128 bits)
compliant: attack predicted the next value: false
*/
