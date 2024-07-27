// RSA key generation: sizes, exponent and what is inside the keys
//
// An RSA key pair is n = p*q (p, q large random primes), a public exponent e
// (almost always 65537) and a private exponent d = e^-1 mod lcm(p-1, q-1).
// Security rests on factoring n, so the modulus size is the security knob:
// per NIST SP 800-57, 2048 bits ~ 112-bit security (the minimum today), 3072
// ~ 128, 7680 ~ 192, 15360 ~ 256. Bigger keys cost a lot: private-key
// operations scale roughly with the cube of the size, and generating a 4096-
// bit key means finding two 2048-bit primes. The JDK's private key keeps the
// CRT parameters (p, q, d mod p-1, ...) for a ~4x faster decryption; they
// are as secret as d. For new designs, elliptic curves give the same security
// with far smaller keys (a 256-bit EC key ~ 3072-bit RSA).
import java.math.BigInteger;
import java.security.InvalidParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAKeyGenParameterSpec;

public class RsaKeyGeneration {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        for (int bits : new int[] {2048, 3072}) {
            kpg.initialize(bits);
            KeyPair kp = kpg.generateKeyPair();
            RSAPublicKey pub = (RSAPublicKey) kp.getPublic();
            RSAPrivateCrtKey priv = (RSAPrivateCrtKey) kp.getPrivate();
            System.out.printf("RSA-%d: modulus %d bits, e=%s, public key (X.509 DER) %d bytes%n",
                    bits, pub.getModulus().bitLength(), pub.getPublicExponent(), pub.getEncoded().length);

            // The math holds: n = p*q, e*d = 1 mod lcm(p-1, q-1)
            BigInteger p = priv.getPrimeP(), q = priv.getPrimeQ();
            BigInteger lcm = lcm(p.subtract(BigInteger.ONE), q.subtract(BigInteger.ONE));
            System.out.printf("  n == p*q: %b, p and q %d bits each, e*d mod lcm == 1: %b%n",
                    p.multiply(q).equals(pub.getModulus()), p.bitLength(),
                    pub.getPublicExponent().multiply(priv.getPrivateExponent()).mod(lcm).equals(BigInteger.ONE));
        }

        // Explicit parameters: size and public exponent. Do not pick e = 3 or a
        // random small e: low exponents were behind several padding attacks.
        kpg.initialize(new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4));
        System.out.println("F4 = " + RSAKeyGenParameterSpec.F4 + " (2^16 + 1)");

        // Too small is refused by the provider
        try {
            kpg.initialize(256);
        } catch (InvalidParameterException e) {
            System.out.println("RSA-256: " + e.getClass().getSimpleName());
        }

        // Rough sizes for comparable security (NIST SP 800-57 part 1)
        System.out.println("security bits -> RSA modulus / EC key: 112 -> 2048 / 224, 128 -> 3072 / 256, "
                + "192 -> 7680 / 384, 256 -> 15360 / 512");
    }

    static BigInteger lcm(BigInteger a, BigInteger b) {
        return a.divide(a.gcd(b)).multiply(b);
    }
}

/* Output:
RSA-2048: modulus 2048 bits, e=65537, public key (X.509 DER) 294 bytes
  n == p*q: true, p and q 1024 bits each, e*d mod lcm == 1: true
RSA-3072: modulus 3072 bits, e=65537, public key (X.509 DER) 422 bytes
  n == p*q: true, p and q 1536 bits each, e*d mod lcm == 1: true
F4 = 65537 (2^16 + 1)
RSA-256: InvalidParameterException
security bits -> RSA modulus / EC key: 112 -> 2048 / 224, 128 -> 3072 / 256, 192 -> 7680 / 384, 256 -> 15360 / 512
*/
