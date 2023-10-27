// NUM13-J. Avoid loss of precision when converting primitive integers to floating-point
// https://wiki.sei.cmu.edu/confluence/display/java/NUM13-J.+Avoid+loss+of+precision+when+converting+primitive+integers+to+floating-point
//
// int -> float and long -> float/double are "widening" conversions, so the
// compiler inserts them silently, yet they can lose information: a float
// has a 24-bit significand (exact integers only up to 2^24 = 16,777,216)
// and a double 53 bits (up to 2^53). Larger values are rounded to the
// nearest representable one, so distinct IDs or amounts collapse into the
// same value and comparisons between them become wrong. Keep integer data
// in integer types; if a conversion is unavoidable, use double for int
// values, or check that the value is within the exact range first.
public class NUM13_IntToFloatPrecision {
    public static void main(String[] args) {
        int big = 16_777_217; // 2^24 + 1
        float f = big;
        System.out.println("int " + big + " as float: " + (int) f + ", lost " + (big - (int) f));

        // A rate limiter: requests are allowed while used < quota
        int quota = 20_000_000;
        int used = 20_000_001;
        System.out.println("noncompliant, over quota? " + overQuotaNoncompliant(used, quota));
        System.out.println("compliant,    over quota? " + overQuotaCompliant(used, quota));

        // long -> double loses precision above 2^53
        long id1 = 9_007_199_254_740_993L; // 2^53 + 1
        long id2 = 9_007_199_254_740_992L; // 2^53
        System.out.println("noncompliant, distinct IDs equal as double? " + ((double) id1 == (double) id2));
        System.out.println("compliant,    distinct IDs equal as long?   " + (id1 == id2));
        System.out.println("range check: 2^53 exact as double? " + fitsInDouble(id2) + ", 2^53 + 1? " + fitsInDouble(id1));
    }

    // Noncompliant: both ints are rounded to the same float
    static boolean overQuotaNoncompliant(int used, int quota) {
        float usage = used; // kept as float for a metrics dashboard
        return usage > quota; // quota is silently converted to float too
    }

    // Compliant: compare the integers themselves
    static boolean overQuotaCompliant(int used, int quota) {
        return used > quota;
    }

    // A long survives the trip through double only if no bits are lost
    // (Long.MAX_VALUE is excluded because the cast back saturates to it)
    static boolean fitsInDouble(long v) {
        return (long) (double) v == v && v != Long.MAX_VALUE;
    }
}

/* Output:
int 16777217 as float: 16777216, lost 1
noncompliant, over quota? false
compliant,    over quota? true
noncompliant, distinct IDs equal as double? true
compliant,    distinct IDs equal as long?   false
range check: 2^53 exact as double? true, 2^53 + 1? false
*/
