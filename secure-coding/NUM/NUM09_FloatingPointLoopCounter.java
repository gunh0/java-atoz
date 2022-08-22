// NUM09-J. Do not use floating-point variables as loop counters
// https://wiki.sei.cmu.edu/confluence/display/java/NUM09-J.+Do+not+use+floating-point+variables+as+loop+counters
//
// A float or double loop counter accumulates rounding error on every
// increment, so the number of iterations depends on representation details
// rather than on the values written in the source. 0.1f added ten times is
// not 1.0f, so a loop "from 0.1 to 1.0" runs nine times. Worse, once a float
// is large enough, adding 1 no longer changes it at all (the spacing between
// adjacent floats exceeds 1), and the loop never ends: a ready-made denial of
// service if an attacker controls the bounds. Count with an integer and
// compute the floating-point value from it.
public class NUM09_FloatingPointLoopCounter {
    public static void main(String[] args) {
        // Expected 10 iterations: 0.1, 0.2, ..., 1.0
        int count = 0;
        for (float x = 0.1f; x <= 1.0f; x += 0.1f) {
            count++;
        }
        System.out.println("noncompliant float counter 0.1..1.0: " + count + " iterations");

        count = 0;
        for (int i = 1; i <= 10; i++) {
            float x = i / 10.0f; // derived from the integer counter
            count++;
        }
        System.out.println("compliant int counter, x = i / 10:   " + count + " iterations");

        // Large values: x++ has no effect, so this loop would never terminate.
        // The guard is only here so the demo stops.
        float start = 100_000_000.0f;
        int guard = 0;
        for (float x = start; x <= start + 10; x++) {
            if (++guard == 1_000) {
                System.out.println("noncompliant float counter from 1e8: still at x == start after 1000 steps: "
                        + (x == start));
                break;
            }
        }
        System.out.println("spacing between floats near 1e8: " + Math.ulp(start));

        count = 0;
        for (int i = 0; i <= 10; i++) {
            count++;
        }
        System.out.println("compliant int counter for 11 steps:  " + count + " iterations");
    }
}

/* Output:
noncompliant float counter 0.1..1.0: 9 iterations
compliant int counter, x = i / 10:   10 iterations
noncompliant float counter from 1e8: still at x == start after 1000 steps: true
spacing between floats near 1e8: 8.0
compliant int counter for 11 steps:  11 iterations
*/
