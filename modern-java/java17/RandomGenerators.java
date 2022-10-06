// JEP 356 — Enhanced Pseudo-Random Number Generators (Java 17)
//
// Random, SplittableRandom and ThreadLocalRandom used to share no common
// type, so code could not be written against "a random generator" and
// swapping algorithms meant rewriting it. Java 17 added the
// java.util.random.RandomGenerator interface (implemented by all three) plus
// a family of new algorithms (the LXM family, Xoshiro, Xoroshiro) that can be
// looked up by name through RandomGeneratorFactory, with properties such as
// splittable, jumpable or streamable. Choose by property, seed for
// reproducibility, and never use any of these for security: that is what
// SecureRandom is for.
import java.util.List;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import java.util.stream.Collectors;

public class RandomGenerators {
    public static void main(String[] args) {
        // One interface for old and new generators
        List<RandomGenerator> generators = List.of(
                new Random(42), new SplittableRandom(42), RandomGenerator.of("L64X128MixRandom"));
        for (RandomGenerator g : generators) {
            System.out.println(g.getClass().getSimpleName() + " is a RandomGenerator, dice roll in range: "
                    + inRange(rollDice(g)));
        }

        // Algorithms by name, with their properties
        RandomGeneratorFactory<RandomGenerator> factory = RandomGeneratorFactory.of("L64X128MixRandom");
        System.out.println(factory.name() + ": group=" + factory.group() + ", stateBits=" + factory.stateBits()
                + ", splittable=" + factory.isSplittable() + ", jumpable=" + factory.isJumpable());

        // Seeded generators are reproducible: same seed, same sequence
        RandomGenerator a = factory.create(2022L);
        RandomGenerator b = factory.create(2022L);
        boolean same = true;
        for (int i = 0; i < 1000; i++) {
            same &= a.nextLong() == b.nextLong();
        }
        System.out.println("same seed, same 1000 values: " + same);

        // Random's algorithm is specified, so this sequence is portable
        System.out.println("new Random(42) ints in [0, 100): " + new Random(42).ints(5, 0, 100)
                .boxed().collect(Collectors.toList()));

        // Pick an algorithm by property instead of by name
        List<String> jumpable = RandomGeneratorFactory.all()
                .filter(RandomGeneratorFactory::isJumpable)
                .map(RandomGeneratorFactory::name)
                .sorted()
                .collect(Collectors.toList());
        System.out.println("jumpable algorithms: " + jumpable);

        // The default algorithm is good for general use
        System.out.println("default generator draws a double in [0, 1): "
                + (RandomGenerator.getDefault().nextDouble() < 1.0));
    }

    // Written against the interface: works with every algorithm
    static int rollDice(RandomGenerator rng) {
        return rng.nextInt(1, 7);
    }

    static boolean inRange(int roll) {
        return roll >= 1 && roll <= 6;
    }
}

/* Output:
Random is a RandomGenerator, dice roll in range: true
SplittableRandom is a RandomGenerator, dice roll in range: true
L64X128MixRandom is a RandomGenerator, dice roll in range: true
L64X128MixRandom: group=LXM, stateBits=192, splittable=true, jumpable=false
same seed, same 1000 values: true
new Random(42) ints in [0, 100): [30, 63, 48, 84, 70]
jumpable algorithms: [Xoroshiro128PlusPlus, Xoshiro256PlusPlus]
default generator draws a double in [0, 1): true
*/
