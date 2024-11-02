// SecureRandom configuration: DRBG parameters, seeding and the SHA1PRNG trap
//
// Why SecureRandom instead of java.util.Random is covered by CERT MSC02-J
// (../../secure-coding/MSC/MSC02_SecureRandom.java). This file is about
// configuring it. Since Java 9 the JDK includes the NIST SP 800-90A
// deterministic random bit generators as SecureRandom "DRBG" (Hash_DRBG,
// HMAC_DRBG, CTR_DRBG), configured with DrbgParameters: security strength,
// prediction resistance and reseeding capability, and a personalization
// string that separates instances. new SecureRandom() is a good default
// (it seeds from the OS); configure DRBG when a standard or a FIPS profile
// demands it. Seeding is where people go wrong: setSeed() on a fresh
// "SHA1PRNG" REPLACES the self-seeding, making the output fully determined by
// your seed; on DRBG and NativePRNG it only adds to the entropy. No random
// bytes are printed here, only properties.
import java.nio.charset.StandardCharsets;
import java.security.DrbgParameters;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.SecureRandomParameters;
import java.util.Arrays;

import static java.security.DrbgParameters.Capability.PR_AND_RESEED;

public class DrbgConfiguration {
    public static void main(String[] args) throws Exception {
        // Default DRBG instance and its parameters
        SecureRandom drbg = SecureRandom.getInstance("DRBG");
        describe("DRBG default", drbg.getParameters());

        // A 256-bit instance with prediction resistance and a personalization string
        SecureRandom strong = SecureRandom.getInstance("DRBG", DrbgParameters.instantiation(
                256, PR_AND_RESEED, "java-atoz session tokens".getBytes(StandardCharsets.UTF_8)));
        describe("DRBG 256/PR_AND_RESEED", strong.getParameters());

        // Per-request parameters: ask for fresh entropy before this output
        byte[] key = new byte[32];
        strong.nextBytes(key, DrbgParameters.nextBytes(256, true, null));
        strong.reseed();
        System.out.println("32-byte key generated with prediction resistance, then reseeded");

        // Asking for more than the mechanism offers fails loudly
        try {
            SecureRandom.getInstance("DRBG", DrbgParameters.instantiation(512, PR_AND_RESEED, null));
        } catch (NoSuchAlgorithmException e) {
            System.out.println("DRBG with strength 512: " + e.getClass().getSimpleName());
        }

        // The seeding trap
        byte[] seed = "2024-04-01".getBytes(StandardCharsets.US_ASCII);
        System.out.println();
        System.out.println("SHA1PRNG, same setSeed() before first use, outputs equal: " + sameOutput("SHA1PRNG", seed));
        System.out.println("DRBG,     same setSeed() before first use, outputs equal: " + sameOutput("DRBG", seed));
    }

    static boolean sameOutput(String alg, byte[] seed) throws Exception {
        SecureRandom a = SecureRandom.getInstance(alg);
        SecureRandom b = SecureRandom.getInstance(alg);
        a.setSeed(seed);
        b.setSeed(seed);
        byte[] x = new byte[32], y = new byte[32];
        a.nextBytes(x);
        b.nextBytes(y);
        return Arrays.equals(x, y);
    }

    static void describe(String label, SecureRandomParameters p) {
        DrbgParameters.Instantiation i = (DrbgParameters.Instantiation) p;
        System.out.printf("%-24s strength %d bits, capability %s, personalized: %b%n",
                label, i.getStrength(), i.getCapability().name(), i.getPersonalizationString() != null);
    }
}

/* Output:
DRBG default             strength 128 bits, capability RESEED_ONLY, personalized: false
DRBG 256/PR_AND_RESEED   strength 256 bits, capability PR_AND_RESEED, personalized: true
32-byte key generated with prediction resistance, then reseeded
DRBG with strength 512: NoSuchAlgorithmException

SHA1PRNG, same setSeed() before first use, outputs equal: true
DRBG,     same setSeed() before first use, outputs equal: false
*/
