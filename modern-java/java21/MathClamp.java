// Java 21 — Math.clamp and StrictMath.clamp (API addition, no JEP)
//
// Forcing a value into a range is everywhere: page sizes, retry delays,
// volume sliders, untrusted "limit" parameters from a request. The usual
// idiom Math.max(min, Math.min(max, value)) is easy to get backwards
// (swap min and max and every input silently becomes the same constant),
// and narrowing a long to an int range needs an extra cast. Java 21 adds
// clamp(value, min, max) for int/long/float/double: the intent is in the
// name, a min > max range throws IllegalArgumentException instead of
// returning nonsense, and clamp(long, int, int) returns an int, so a long
// input is narrowed safely without overflow.
public class MathClamp {
    static final int MIN_PAGE = 1;
    static final int MAX_PAGE = 100;

    public static void main(String[] args) {
        long[] requested = {-5, 0, 20, 100, 4_294_967_295L};

        for (long r : requested) {
            // Before: the max/min idiom, plus a cast (overflows for large longs if done first)
            int before = (int) Math.max(MIN_PAGE, Math.min(MAX_PAGE, r));
            int truncatedFirst = Math.max(MIN_PAGE, Math.min(MAX_PAGE, (int) r)); // the classic bug
            // After: one call, returns int for an int range even when the input is long
            int after = Math.clamp(r, MIN_PAGE, MAX_PAGE);
            System.out.printf("limit=%-11d before %3d  cast-first bug %3d  clamp %3d%n", r, before, truncatedFirst, after);
        }

        // Swapped bounds: the idiom quietly returns a constant, clamp refuses
        int value = 42;
        System.out.println("swapped idiom: " + Math.max(MAX_PAGE, Math.min(MIN_PAGE, value)));
        try {
            Math.clamp(value, MAX_PAGE, MIN_PAGE);
        } catch (IllegalArgumentException e) {
            System.out.println("swapped clamp: IllegalArgumentException (" + e.getMessage() + ")");
        }

        // Floating point: NaN stays NaN, and -0.0 / 0.0 are ordered
        System.out.println("clamp(1.7, 0.0, 1.0)  = " + Math.clamp(1.7, 0.0, 1.0));
        System.out.println("clamp(NaN, 0.0, 1.0)  = " + Math.clamp(Double.NaN, 0.0, 1.0));
        System.out.println("clamp(-0.0, 0.0, 1.0) = " + Math.clamp(-0.0, 0.0, 1.0));
        System.out.println("clamp(0.25f, 0.5f, 1f) = " + Math.clamp(0.25f, 0.5f, 1f));

        // Exponential backoff capped at 30 s
        StringBuilder delays = new StringBuilder();
        for (int attempt = 0; attempt < 8; attempt++) {
            delays.append(Math.clamp(100L << (attempt * 2), 100L, 30_000L)).append(' ');
        }
        System.out.println("backoff ms: " + delays.toString().trim());
    }
}

/* Output:
limit=-5          before   1  cast-first bug   1  clamp   1
limit=0           before   1  cast-first bug   1  clamp   1
limit=20          before  20  cast-first bug  20  clamp  20
limit=100         before 100  cast-first bug 100  clamp 100
limit=4294967295  before 100  cast-first bug   1  clamp 100
swapped idiom: 100
swapped clamp: IllegalArgumentException (100 > 1)
clamp(1.7, 0.0, 1.0)  = 1.0
clamp(NaN, 0.0, 1.0)  = NaN
clamp(-0.0, 0.0, 1.0) = 0.0
clamp(0.25f, 0.5f, 1f) = 0.5
backoff ms: 100 400 1600 6400 25600 30000 30000 30000
*/
