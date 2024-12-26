// java.util.random (Java 17) puts every pseudo-random generator behind one
// interface, RandomGenerator, which Random, SplittableRandom, SecureRandom
// and ThreadLocalRandom all implement. New algorithms (the LXM family such as
// L64X128MixRandom, Xoshiro256PlusPlus, ...) are picked by name through
// RandomGenerator.of / RandomGeneratorFactory, so code can depend on the
// interface and swap the algorithm.
//  - a seed makes a sequence reproducible (simulations, tests); never seed
//    anything security-related, use SecureRandom for tokens and keys
//  - SplittableGenerator.split() / JumpableGenerator give independent
//    streams for parallel work without sharing one locked generator
// Output shows properties of the sequences, not the random numbers.
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import java.util.stream.DoubleStream;

public class RandomGeneratorsExample {
    static long[] firstFive(RandomGenerator g) {
        return g.longs(5).toArray();
    }

    // Works with any algorithm: code against the interface
    static int rollDice(RandomGenerator g) {
        return g.nextInt(1, 7); // origin inclusive, bound exclusive
    }

    public static void main(String[] args) {
        // Picking algorithms by name
        for (String name : List.of("L64X128MixRandom", "Xoshiro256PlusPlus", "SplittableRandom", "Random")) {
            RandomGeneratorFactory<RandomGenerator> f = RandomGeneratorFactory.of(name);
            System.out.printf("%-20s stateBits=%-4d splittable=%-5b jumpable=%b%n",
                    f.name(), f.stateBits(), f.isSplittable(), f.isJumpable());
        }

        // Same seed, same sequence; different seed, different sequence
        RandomGeneratorFactory<RandomGenerator> lxm = RandomGeneratorFactory.of("L64X128MixRandom");
        long[] a = firstFive(lxm.create(2024));
        long[] b = firstFive(lxm.create(2024));
        long[] c = firstFive(lxm.create(2025));
        System.out.println("seed 2024 twice equal: " + Arrays.equals(a, b));
        System.out.println("seed 2024 vs 2025 equal: " + Arrays.equals(a, c));

        // Bounded values stay in range, and a fair die is roughly uniform
        RandomGenerator dice = lxm.create(7);
        int[] counts = new int[7];
        for (int i = 0; i < 60_000; i++) counts[rollDice(dice)]++;
        boolean uniform = Arrays.stream(counts, 1, 7).allMatch(n -> Math.abs(n - 10_000) < 500);
        System.out.println("die faces 1..6 only: " + (counts[0] == 0) + ", roughly uniform: " + uniform);

        // Streams of random values
        RandomGenerator g = lxm.create(1);
        System.out.println("ints(1000, 10, 20) within [10,20): " + g.ints(1000, 10, 20).allMatch(x -> x >= 10 && x < 20));
        double mean = g.doubles(100_000).average().orElseThrow();
        System.out.println("mean of doubles() close to 0.5: " + (Math.abs(mean - 0.5) < 0.01));
        double gaussianMean = DoubleStream.generate(() -> g.nextGaussian(100, 15)).limit(100_000).average().orElseThrow();
        System.out.println("nextGaussian(100, 15) mean close to 100: " + (Math.abs(gaussianMean - 100) < 0.5));

        // split(): independent child generators for parallel tasks
        RandomGenerator.SplittableGenerator parent = RandomGeneratorFactory.<RandomGenerator.SplittableGenerator>of("L64X128MixRandom").create(99);
        long[] child1 = firstFive(parent.split());
        long[] child2 = firstFive(parent.split());
        System.out.println("split children differ: " + !Arrays.equals(child1, child2));

        // Legacy java.util.Random is a RandomGenerator too; its algorithm is fixed by its spec,
        // so a seeded Random gives the same numbers on every JVM
        Random legacy = new Random(42);
        int[] fromLegacy = new int[5];
        for (int i = 0; i < fromLegacy.length; i++) fromLegacy[i] = legacy.nextInt(100);
        System.out.println("new Random(42), 5 x nextInt(100): " + Arrays.toString(fromLegacy));

        // SecureRandom fits the same interface: use it for anything an attacker must not predict
        RandomGenerator secure = new SecureRandom();
        byte[] token = new byte[16];
        ((SecureRandom) secure).nextBytes(token);
        System.out.println("SecureRandom is a RandomGenerator: " + (secure instanceof RandomGenerator)
                + ", token bytes: " + token.length);
    }
}

/* Output:
L64X128MixRandom     stateBits=192  splittable=true  jumpable=false
Xoshiro256PlusPlus   stateBits=256  splittable=false jumpable=true
SplittableRandom     stateBits=64   splittable=true  jumpable=false
Random               stateBits=48   splittable=false jumpable=false
seed 2024 twice equal: true
seed 2024 vs 2025 equal: false
die faces 1..6 only: true, roughly uniform: true
ints(1000, 10, 20) within [10,20): true
mean of doubles() close to 0.5: true
nextGaussian(100, 15) mean close to 100: true
split children differ: true
new Random(42), 5 x nextInt(100): [30, 63, 48, 84, 70]
SecureRandom is a RandomGenerator: true, token bytes: 16
*/
