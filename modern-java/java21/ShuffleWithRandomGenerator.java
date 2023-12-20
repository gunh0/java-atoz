// Java 21 — Collections.shuffle(List, RandomGenerator) (API addition, no JEP)
//
// Java 17 (JEP 356) introduced the RandomGenerator interface and a family
// of better algorithms (LXM generators such as L64X128MixRandom, Xoshiro,
// SplittableRandom), but Collections.shuffle still demanded a
// java.util.Random. A SplittableRandom or an LXM generator could not be
// passed in; you had to write Fisher-Yates yourself or wrap the generator
// with Random.from() (Java 19). Java 21 adds shuffle(List, RandomGenerator),
// so any generator works: a seeded, reproducible one for tests and
// simulations, or SecureRandom when the order must be unpredictable
// (draws, token lists, mixing anonymous ballots).
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ShuffleWithRandomGenerator {
    public static void main(String[] args) {
        List<Integer> base = IntStream.rangeClosed(1, 10).boxed().collect(Collectors.toList());

        // Before (Java 17): a hand-written Fisher-Yates for a non-Random generator
        RandomGenerator lxm = RandomGeneratorFactory.of("L64X128MixRandom").create(42);
        List<Integer> manual = new ArrayList<>(base);
        for (int i = manual.size() - 1; i > 0; i--) {
            Collections.swap(manual, i, lxm.nextInt(i + 1));
        }
        System.out.println("hand-written:          " + manual);

        // Before (Java 19-20): adapt the generator to a Random
        List<Integer> adapted = new ArrayList<>(base);
        Collections.shuffle(adapted, Random.from(RandomGeneratorFactory.of("L64X128MixRandom").create(42)));
        System.out.println("Random.from adapter:   " + adapted);

        // After: pass any RandomGenerator directly
        List<Integer> direct = new ArrayList<>(base);
        Collections.shuffle(direct, RandomGeneratorFactory.of("L64X128MixRandom").create(42));
        System.out.println("shuffle(list, LXM):    " + direct);

        // Same algorithm and seed -> the same order: reproducible tests and simulations
        List<Integer> replay = new ArrayList<>(base);
        Collections.shuffle(replay, RandomGeneratorFactory.of("L64X128MixRandom").create(42));
        System.out.println("replayed with seed 42 is identical: " + replay.equals(direct));

        // SplittableRandom never extended Random; now it can shuffle too
        List<Integer> split = new ArrayList<>(base);
        Collections.shuffle(split, new SplittableRandom(7));
        System.out.println("SplittableRandom(7) keeps all elements: "
                + split.stream().sorted().collect(Collectors.toList()).equals(base));

        // When order must not be predictable, SecureRandom is still the choice (it is a Random)
        List<String> tickets = new ArrayList<>(List.of("A-17", "B-02", "C-33", "D-48"));
        Collections.shuffle(tickets, new SecureRandom());
        System.out.println("SecureRandom draw size: " + tickets.size() + ", same tickets: "
                + tickets.stream().sorted().collect(Collectors.toList()).equals(List.of("A-17", "B-02", "C-33", "D-48")));
    }
}

/* Output:
hand-written:          [5, 7, 10, 3, 6, 4, 1, 2, 8, 9]
Random.from adapter:   [5, 7, 10, 3, 6, 4, 1, 2, 8, 9]
shuffle(list, LXM):    [5, 7, 10, 3, 6, 4, 1, 2, 8, 9]
replayed with seed 42 is identical: true
SplittableRandom(7) keeps all elements: true
SecureRandom draw size: 4, same tickets: true
*/
