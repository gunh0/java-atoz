// Effective Java, Item 59: Know and use the libraries.
//
// "Random number in [0, n)" looks like a one-liner: Math.abs(rnd.nextInt())
// % n. It is wrong twice. If n is not a power of two, some values come up
// far more often than others, and Math.abs(Integer.MIN_VALUE) is still
// negative, so the result can be out of range. Random.nextInt(int) gets it
// right, was written by experts, is tested by millions of users and gets
// faster with every release for free. The same applies across java.lang,
// java.util, java.util.concurrent and java.io: learn what is there (each
// release adds more, e.g. InputStream.transferTo in Java 9) before writing
// your own. Use ThreadLocalRandom for most randomness, SplittableRandom for
// fork-join work, and SecureRandom for anything security-related.
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

public class KnowTheLibraries {
    static final int SAMPLES = 1_000_000;

    public static void main(String[] args) throws IOException {
        // n is a bit less than 2/3 of 2^31: the home-made method is badly skewed
        int n = 2 * (Integer.MAX_VALUE / 3);

        Random rnd = new Random(42); // fixed seed: deterministic output
        int low = 0;
        for (int i = 0; i < SAMPLES; i++) {
            if (homeMade(rnd, n) < n / 2) low++;
        }
        System.out.printf("Math.abs(nextInt()) %% n: %.1f%% in the lower half%n", 100.0 * low / SAMPLES);

        rnd = new Random(42);
        low = 0;
        for (int i = 0; i < SAMPLES; i++) {
            if (rnd.nextInt(n) < n / 2) low++;
        }
        System.out.printf("Random.nextInt(n):        %.1f%% in the lower half%n", 100.0 * low / SAMPLES);

        // The corner case that breaks the range
        System.out.println("Math.abs(Integer.MIN_VALUE) = " + Math.abs(Integer.MIN_VALUE));
        System.out.println("  % 10 = " + Math.abs(Integer.MIN_VALUE) % 10 + "  (negative: out of [0, 10))");
        System.out.println("Math.floorMod(Integer.MIN_VALUE, 10) = " + Math.floorMod(Integer.MIN_VALUE, 10));

        // Copying a stream: no hand-written buffer loop needed
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long copied = new ByteArrayInputStream("library code".getBytes()).transferTo(out);
        System.out.println("transferTo copied " + copied + " bytes: " + out);
    }

    // Don't do this
    static int homeMade(Random rnd, int n) {
        return Math.abs(rnd.nextInt()) % n;
    }
}

/* Output:
Math.abs(nextInt()) % n: 66.6% in the lower half
Random.nextInt(n):        50.0% in the lower half
Math.abs(Integer.MIN_VALUE) = -2147483648
  % 10 = -8  (negative: out of [0, 10))
Math.floorMod(Integer.MIN_VALUE, 10) = 2
transferTo copied 12 bytes: library code
*/
