// NUM01-J. Do not perform bitwise and arithmetic operations on the same data
// https://wiki.sei.cmu.edu/confluence/display/java/NUM01-J.+Do+not+perform+bitwise+and+arithmetic+operations+on+the+same+data
//
// Shifts look like fast multiplication and division, and masks look like
// fast remainders, but they are equivalent only for non-negative values.
// For a negative int, x >> 1 rounds toward negative infinity while x / 2
// rounds toward zero, and x & 7 is not x % 8. Mixing the two views of the
// same variable (sometimes a number, sometimes a bit pattern) produces
// results that are right in tests with positive data and wrong in
// production. Treat a value either as a number (use +, -, *, /, %, and
// Math.floorDiv/floorMod when you want floor semantics) or as bits (use
// &, |, ^, <<, >>, >>>), and let the JIT pick the fast instructions.
public class NUM01_BitwiseAndArithmetic {
    public static void main(String[] args) {
        int[] values = {14, -14, -1, -7};

        System.out.println("x      x>>1  x/2   floorDiv | x&7  x%8  floorMod");
        for (int x : values) {
            System.out.printf("%-6d %-5d %-5d %-8d | %-4d %-4d %d%n",
                    x, x >> 1, x / 2, Math.floorDiv(x, 2), x & 7, x % 8, Math.floorMod(x, 8));
        }

        // A bucket index computed by masking a hash, then by arithmetic
        int hash = "attacker".hashCode() | 0x8000_0000; // force a negative hash
        System.out.println("negative hash: " + (hash < 0));
        int bucketsLen = 16;
        int arithmeticIndex = hash % bucketsLen; // noncompliant: may be negative -> AIOOBE
        System.out.println("noncompliant: hash % 16 = " + arithmeticIndex + " (valid index? " + (arithmeticIndex >= 0) + ")");
        int floorIndex = Math.floorMod(hash, bucketsLen); // compliant: always 0..15
        System.out.println("compliant:    floorMod(hash, 16) = " + floorIndex);

        // "Fast" division by shifting: same as / only for non-negative values
        int balance = -5;
        System.out.println("noncompliant: half of " + balance + " via >> 1 = " + (balance >> 1));
        System.out.println("compliant:    half of " + balance + " via / 2  = " + (balance / 2));
    }
}

/* Output:
x      x>>1  x/2   floorDiv | x&7  x%8  floorMod
14     7     7     7        | 6    6    6
-14    -7    -7    -7       | 2    -6   2
-1     -1    0     -1       | 7    -1   7
-7     -4    -3    -4       | 1    -7   1
negative hash: true
noncompliant: hash % 16 = -11 (valid index? false)
compliant:    floorMod(hash, 16) = 5
noncompliant: half of -5 via >> 1 = -3
compliant:    half of -5 via / 2  = -2
*/
