// BigInteger is an immutable arbitrary-precision integer. int and long wrap
// around silently on overflow; BigInteger just grows. It also carries the
// number theory that public-key cryptography is built on: modPow, modInverse,
// gcd, isProbablePrime.
//  - create with valueOf(long), new BigInteger(String[, radix]) or from bytes
//  - Math.addExact/multiplyExact throw instead of wrapping when you only
//    need to detect overflow
//  - longValueExact()/intValueExact() convert back and throw if it does not fit
// Below: overflow, factorials, radix conversion and textbook RSA with tiny
// primes (illustration only: real RSA needs 2048+ bit keys and padding,
// so use java.security / javax.crypto, never hand-rolled RSA).
import java.math.BigInteger;

public class BigIntegerExample {
    static BigInteger factorial(int n) {
        BigInteger result = BigInteger.ONE;
        for (int i = 2; i <= n; i++) result = result.multiply(BigInteger.valueOf(i));
        return result;
    }

    public static void main(String[] args) {
        long max = Long.MAX_VALUE;
        System.out.println("Long.MAX_VALUE + 1        = " + (max + 1));
        System.out.println("BigInteger MAX + 1        = " + BigInteger.valueOf(max).add(BigInteger.ONE));
        try {
            Math.addExact(max, 1);
        } catch (ArithmeticException e) {
            System.out.println("Math.addExact(MAX, 1)     -> ArithmeticException: " + e.getMessage());
        }
        System.out.println("20! as long               = " + factorial(20).longValueExact());
        BigInteger f30 = factorial(30);
        System.out.println("30! = " + f30 + " (" + f30.bitLength() + " bits)");
        try {
            f30.longValueExact();
        } catch (ArithmeticException e) {
            System.out.println("30!.longValueExact()      -> ArithmeticException");
        }

        // Radix conversion, e.g. for hex digests or IDs
        BigInteger fromHex = new BigInteger("deadbeefcafebabe", 16);
        System.out.println("0xdeadbeefcafebabe        = " + fromHex + ", back to hex: " + fromHex.toString(16));
        System.out.println("2^100                     = " + BigInteger.TWO.pow(100));
        System.out.println("gcd(462, 1071)            = " + BigInteger.valueOf(462).gcd(BigInteger.valueOf(1071)));

        // Textbook RSA with toy primes
        BigInteger p = BigInteger.valueOf(61), q = BigInteger.valueOf(53);
        System.out.println("61 and 53 probably prime: " + (p.isProbablePrime(20) && q.isProbablePrime(20)));
        BigInteger n = p.multiply(q);
        BigInteger phi = p.subtract(BigInteger.ONE).multiply(q.subtract(BigInteger.ONE));
        BigInteger e = BigInteger.valueOf(17);
        BigInteger d = e.modInverse(phi);
        System.out.println("n=" + n + " phi=" + phi + " e=" + e + " d=" + d);
        BigInteger message = BigInteger.valueOf(65);
        BigInteger cipher = message.modPow(e, n);
        BigInteger decrypted = cipher.modPow(d, n);
        System.out.println("encrypt 65 -> " + cipher + ", decrypt -> " + decrypted);
        // Textbook RSA is malleable: multiplying ciphertexts multiplies plaintexts
        BigInteger cipher2 = BigInteger.TWO.modPow(e, n);
        System.out.println("decrypt(c(65) * c(2)) = " + cipher.multiply(cipher2).mod(n).modPow(d, n)
                + " (= 65 * 2, without knowing either plaintext)");
    }
}

/* Output:
Long.MAX_VALUE + 1        = -9223372036854775808
BigInteger MAX + 1        = 9223372036854775808
Math.addExact(MAX, 1)     -> ArithmeticException: long overflow
20! as long               = 2432902008176640000
30! = 265252859812191058636308480000000 (108 bits)
30!.longValueExact()      -> ArithmeticException
0xdeadbeefcafebabe        = 16045690984503098046, back to hex: deadbeefcafebabe
2^100                     = 1267650600228229401496703205376
gcd(462, 1071)            = 21
61 and 53 probably prime: true
n=3233 phi=3120 e=17 d=2753
encrypt 65 -> 2790, decrypt -> 65
decrypt(c(65) * c(2)) = 130 (= 65 * 2, without knowing either plaintext)
*/
