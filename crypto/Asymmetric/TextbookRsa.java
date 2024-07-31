// Textbook RSA: why raw m^e mod n must never be used directly
//
// The RSA function c = m^e mod n is a trapdoor permutation, not an
// encryption scheme. Used raw ("RSA/ECB/NoPadding" in Java) it is
// deterministic, so an attacker can encrypt guesses and compare (a yes/no
// answer, a PIN, a salary band leaks immediately), and it is multiplicatively
// homomorphic: E(a) * E(b) = E(a*b), so ciphertexts can be transformed
// without the key. A small message with small e may not even wrap around n,
// and then the ciphertext is just m^e over the integers. Padding schemes fix
// this by randomizing and structuring the input: OAEP for encryption, PSS
// for signatures. The JDK's NoPadding RSA computes exactly BigInteger.modPow,
// which this program uses to show the math.
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import javax.crypto.Cipher;

public class TextbookRsa {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        RSAPublicKey pub = (RSAPublicKey) kp.getPublic();
        RSAPrivateKey priv = (RSAPrivateKey) kp.getPrivate();
        BigInteger n = pub.getModulus(), e = pub.getPublicExponent(), d = priv.getPrivateExponent();

        // 1. Java's NoPadding RSA is exactly m^e mod n
        byte[] vote = "yes".getBytes(StandardCharsets.US_ASCII);
        Cipher raw = Cipher.getInstance("RSA/ECB/NoPadding");
        raw.init(Cipher.ENCRYPT_MODE, pub);
        byte[] c1 = raw.doFinal(vote);
        System.out.println("Cipher NoPadding == m^e mod n: "
                + new BigInteger(1, c1).equals(new BigInteger(1, vote).modPow(e, n)));

        // 2. Deterministic: the attacker encrypts both candidate votes and compares
        for (String guess : new String[] {"no", "yes"}) {
            raw.init(Cipher.ENCRYPT_MODE, pub);
            boolean match = Arrays.equals(raw.doFinal(guess.getBytes(StandardCharsets.US_ASCII)), c1);
            System.out.println("attacker tries \"" + guess + "\": " + (match ? "match" : "no match"));
        }

        // 3. Malleable: multiply a ciphertext by 2^e and the plaintext doubles
        BigInteger salary = BigInteger.valueOf(50_000);
        BigInteger c = salary.modPow(e, n);
        BigInteger cForged = c.multiply(BigInteger.TWO.modPow(e, n)).mod(n);
        System.out.println("decrypt(c * 2^e) = " + cForged.modPow(d, n) + " (original 50000)");

        // 4. With e = 65537 a small m wraps around n, but with e = 3 and an
        //    unpadded 16-byte message m^3 < n: the "ciphertext" is a perfect cube.
        BigInteger m = new BigInteger(1, "secret key 16 by".getBytes(StandardCharsets.US_ASCII));
        BigInteger cube = m.pow(3);
        System.out.println("e=3: m^3 smaller than a 2048-bit n: " + (cube.bitLength() < 2048)
                + ", integer cube root recovers m: " + cubeRoot(cube).equals(m));

        // 5. With OAEP the same message encrypts differently every time
        Cipher oaep = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        oaep.init(Cipher.ENCRYPT_MODE, pub);
        byte[] o1 = oaep.doFinal(vote);
        oaep.init(Cipher.ENCRYPT_MODE, pub);
        System.out.println("OAEP: two encryptions of \"yes\" equal: " + Arrays.equals(o1, oaep.doFinal(vote)));
    }

    static BigInteger cubeRoot(BigInteger x) {
        BigInteger lo = BigInteger.ZERO, hi = BigInteger.ONE.shiftLeft(x.bitLength() / 3 + 1);
        while (lo.compareTo(hi) < 0) {
            BigInteger mid = lo.add(hi).add(BigInteger.ONE).shiftRight(1);
            if (mid.pow(3).compareTo(x) <= 0) lo = mid; else hi = mid.subtract(BigInteger.ONE);
        }
        return lo;
    }
}

/* Output:
Cipher NoPadding == m^e mod n: true
attacker tries "no": no match
attacker tries "yes": match
decrypt(c * 2^e) = 100000 (original 50000)
e=3: m^3 smaller than a 2048-bit n: true, integer cube root recovers m: true
OAEP: two encryptions of "yes" equal: false
*/
