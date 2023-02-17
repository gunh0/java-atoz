// NUM02-J. Ensure that division and remainder operations do not result in divide-by-zero errors
// https://wiki.sei.cmu.edu/confluence/display/java/NUM02-J.+Ensure+that+division+and+remainder+operations+do+not+result+in+divide-by-zero+errors
//
// Integer / and % throw ArithmeticException when the divisor is zero. If the
// divisor comes from input (a page size, a shard count, a rate limit), an
// attacker can send 0 and crash the request, or skip the code after the
// division. Floating-point division does not throw at all: it quietly yields
// Infinity or NaN, which then poisons every later comparison. Check the
// divisor before dividing and treat 0 as invalid input.
public class NUM02_DivideByZero {
    public static void main(String[] args) {
        int[] shardCounts = {4, 0};
        for (int shards : shardCounts) {
            System.out.println("shards=" + shards);
            System.out.println("  noncompliant: " + shardNoncompliant("user-42", shards));
            System.out.println("  compliant:    " + shardCompliant("user-42", shards));
        }

        // Floating point: no exception, just a value that breaks later checks
        double total = 120.0;
        int responses = 0;
        double average = total / responses;
        System.out.println("double average with 0 responses: " + average + ", above limit 100? " + (average > 100));
        double ratio = 0.0 / responses;
        System.out.println("0.0 / 0 = " + ratio + ", above limit 100? " + (ratio > 100) + ", below? " + (ratio < 100));
        System.out.println("compliant average: " + averageCompliant(total, responses));
    }

    // Noncompliant: an attacker-chosen 0 makes % throw
    static String shardNoncompliant(String key, int shards) {
        try {
            return "shard " + Math.floorMod(key.length(), shards);
        } catch (ArithmeticException e) {
            return "ArithmeticException: " + e.getMessage();
        }
    }

    // Compliant: validate the divisor first
    static String shardCompliant(String key, int shards) {
        if (shards <= 0) {
            return "rejected (shard count must be positive)";
        }
        return "shard " + Math.floorMod(key.length(), shards);
    }

    static String averageCompliant(double total, int count) {
        return count == 0 ? "n/a (no responses)" : String.valueOf(total / count);
    }
}

/* Output:
shards=4
  noncompliant: shard 3
  compliant:    shard 3
shards=0
  noncompliant: ArithmeticException: / by zero
  compliant:    rejected (shard count must be positive)
double average with 0 responses: Infinity, above limit 100? true
0.0 / 0 = NaN, above limit 100? false, below? false
compliant average: n/a (no responses)
*/
