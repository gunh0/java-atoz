// A generic method declares its own type parameters before the return type:
//   static <T> T firstOrDefault(List<T> list, T fallback)
// They are independent of any class type parameter, so even a plain utility
// class can have them. The compiler INFERS T from the arguments (and from the
// assignment target), so callers rarely write it; when inference needs help,
// an explicit type witness is written as Util.<String>emptyList().
// Typical uses: utility methods (swap, max, toMap), factories and converters.
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class GenericMethodExample {
    public static void main(String[] args) {
        // T inferred as String, then as Integer
        System.out.println(firstOrDefault(List.of("alpha", "beta"), "none"));
        System.out.println(firstOrDefault(List.<Integer>of(), -1));

        // Works on any array type
        String[] hosts = {"db", "web", "cache"};
        swap(hosts, 0, 2);
        System.out.println("swapped: " + Arrays.toString(hosts));
        Integer[] codes = {200, 404};
        swap(codes, 0, 1);
        System.out.println("swapped: " + Arrays.toString(codes));

        // Two type parameters: K inferred from the key function, V from the list
        List<User> users = List.of(new User(7, "kim"), new User(3, "lee"));
        Map<Integer, User> byId = indexBy(users, User::id);
        System.out.println("byId.get(3) = " + byId.get(3).name());

        // Generic varargs factory
        List<String> list = listOf("x", "y", "z");
        System.out.println("listOf: " + list);

        // Explicit type witness: needed when there is nothing to infer from
        System.out.println("empty: " + GenericMethodExample.<String>emptyMutable().size());

        // Inference picks the most specific common supertype
        List<? extends Number> mixed = listOf(1, 2.5, 3L);
        System.out.println("mixed numbers: " + mixed);
    }

    static <T> T firstOrDefault(List<T> list, T fallback) {
        return list.isEmpty() ? fallback : list.get(0);
    }

    static <T> void swap(T[] array, int i, int j) {
        T tmp = array[i];
        array[i] = array[j];
        array[j] = tmp;
    }

    static <K, V> Map<K, V> indexBy(List<V> values, Function<V, K> key) {
        Map<K, V> map = new HashMap<>();
        for (V v : values) {
            map.put(key.apply(v), v);
        }
        return map;
    }

    @SafeVarargs
    static <T> List<T> listOf(T... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    static <T> List<T> emptyMutable() {
        return new ArrayList<>();
    }
}

record User(int id, String name) { }

/* Output:
alpha
-1
swapped: [cache, web, db]
swapped: [404, 200]
byId.get(3) = lee
listOf: [x, y, z]
empty: 0
mixed numbers: [1, 2.5, 3]
*/
