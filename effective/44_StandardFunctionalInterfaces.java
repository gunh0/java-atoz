// Effective Java, Item 44: Favor the use of standard functional interfaces.
//
// java.util.function has 43 interfaces, but six cover most needs:
// UnaryOperator<T>, BinaryOperator<T>, Predicate<T>, Function<T,R>,
// Supplier<T> and Consumer<T>, plus int/long/double variants that avoid
// boxing (IntPredicate, LongBinaryOperator, ToIntFunction ...). Use them
// instead of declaring look-alike interfaces: users already know them and
// they come with useful default methods (and, negate, andThen, compose).
// Write your own only if it will be widely used and benefits from a
// descriptive name, has a strong contract, or needs custom default methods
// (Comparator is the classic case). Always annotate it @FunctionalInterface,
// and do not overload methods to take different functional interfaces in the
// same argument position.
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class StandardFunctionalInterfaces {
    public static void main(String[] args) {
        // A home-grown interface: works, but every reader must look it up
        Cache<String, Integer> custom = new Cache<>((map, eldest) -> map.size() > 2);
        // The standard BiPredicate says the same thing
        BoundedCache<String, Integer> standard = new BoundedCache<>((map, eldest) -> map.size() > 2);
        for (String k : new String[] { "a", "b", "c", "d" }) {
            custom.put(k, k.charAt(0) - 'a');
            standard.put(k, k.charAt(0) - 'a');
        }
        System.out.println("custom interface:   " + custom);
        System.out.println("BiPredicate:        " + standard);

        // The six basic interfaces
        UnaryOperator<String> trim = String::strip;
        BinaryOperator<Integer> max = BinaryOperator.maxBy(Integer::compare);
        Predicate<String> empty = String::isEmpty;
        Function<String, Integer> length = String::length;
        Supplier<StringBuilder> sb = StringBuilder::new;
        Consumer<Object> print = x -> System.out.println("  consumed: " + x);

        System.out.println("UnaryOperator:      [" + trim.apply("  pad  ") + "]");
        System.out.println("BinaryOperator:     " + max.apply(3, 7));
        System.out.println("Predicate.negate(): " + empty.negate().test(""));
        System.out.println("Function.andThen(): " + length.andThen(n -> n * n).apply("four"));
        System.out.println("Supplier:           " + sb.get().append("built").toString());
        print.andThen(x -> System.out.println("  then: " + x)).accept("event");

        // Primitive specialisations: no Integer boxing
        IntPredicate even = n -> n % 2 == 0;
        System.out.println("IntPredicate:       " + even.and(n -> n > 2).test(4));
    }
}

// The kind of interface you do NOT need to write: it is just BiPredicate<Map<K,V>, Map.Entry<K,V>>
@FunctionalInterface
interface EldestEntryRemovalFunction<K, V> {
    boolean remove(Map<K, V> map, Map.Entry<K, V> eldest);
}

class Cache<K, V> extends LinkedHashMap<K, V> {
    private final EldestEntryRemovalFunction<K, V> policy;

    Cache(EldestEntryRemovalFunction<K, V> policy) {
        this.policy = policy;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return policy.remove(this, eldest);
    }
}

class BoundedCache<K, V> extends LinkedHashMap<K, V> {
    private final BiPredicate<Map<K, V>, Map.Entry<K, V>> policy;

    BoundedCache(BiPredicate<Map<K, V>, Map.Entry<K, V>> policy) {
        this.policy = policy;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return policy.test(this, eldest);
    }
}

/* Output:
custom interface:   {c=2, d=3}
BiPredicate:        {c=2, d=3}
UnaryOperator:      [pad]
BinaryOperator:     7
Predicate.negate(): false
Function.andThen(): 16
Supplier:           built
  consumed: event
  then: event
IntPredicate:       true
*/
