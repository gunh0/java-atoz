// Compact strings: one byte per char for Latin-1, two for everything else
//
// Since Java 9 (JEP 254) a String stores its characters in a byte[] plus a
// "coder" flag: LATIN1 (one byte per char) when every char is <= U+00FF,
// UTF16 (two bytes per char) otherwise. Most strings in typical heaps are
// ASCII, so this roughly halves their footprint; the API is unchanged, and
// charAt/length still count UTF-16 code units. The coder is private, but
// its effect is observable: HotSpot's ThreadMXBean extension reports how
// many bytes the current thread allocated, so we can measure creating
// 100,000-char strings of each kind. Note that a single non-Latin-1 char
// switches the WHOLE string to UTF16. (-XX:-CompactStrings turns the
// feature off.) Allocation counts include the String object and array
// headers, so the program prints rounded kilobytes and compares with a
// tolerance.
import java.lang.management.ManagementFactory;

public class CompactStrings {
    static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    static String sink;

    public static void main(String[] args) {
        int n = 100_000;
        String prefix = "a".repeat(n - 1);

        long ascii = allocated(() -> "a".repeat(n));
        long latin1 = allocated(() -> prefix + "\u00e9"); // e-acute: still Latin-1
        long oneHangul = allocated(() -> prefix + "\ud55c"); // Hangul syllable: needs UTF-16
        long hangul = allocated(() -> "\ud55c".repeat(n));
        System.out.printf("%-34s %4d KB%n", n + " x 'a'", kb(ascii));
        System.out.printf("%-34s %4d KB, same as ASCII: %b%n", (n - 1) + " x 'a' + U+00E9", kb(latin1), near(latin1, ascii));
        System.out.printf("%-34s %4d KB, twice ASCII: %b%n", (n - 1) + " x 'a' + U+D55C", kb(oneHangul), near(oneHangul, 2 * ascii));
        System.out.printf("%-34s %4d KB, twice ASCII: %b%n", n + " x U+D55C", kb(hangul), near(hangul, 2 * ascii));

        // The API does not change: length() and charAt() count UTF-16 units
        String mixed = prefix + "\ud55c";
        System.out.println("length() of 'a'-only and mixed: " + "a".repeat(n).length() + " / " + mixed.length()
                + ", last char: U+" + Integer.toHexString(mixed.charAt(n - 1)).toUpperCase());
    }

    interface Maker { String make(); }

    // Bytes allocated while creating one string (best of a few runs, to skip one-time costs)
    static long allocated(Maker m) {
        long best = Long.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            long before = THREADS.getCurrentThreadAllocatedBytes();
            sink = m.make();
            best = Math.min(best, THREADS.getCurrentThreadAllocatedBytes() - before);
        }
        return best;
    }

    static long kb(long bytes) {
        return Math.round(bytes / 1000.0);
    }

    static boolean near(long actual, long expected) {
        return Math.abs(actual - expected) < expected / 20; // within 5%
    }
}

/* Output:
100000 x 'a'                        100 KB
99999 x 'a' + U+00E9                100 KB, same as ASCII: true
99999 x 'a' + U+D55C                200 KB, twice ASCII: true
100000 x U+D55C                     200 KB, twice ASCII: true
length() of 'a'-only and mixed: 100000 / 100000, last char: U+D55C
*/
