// Java 16 — Stream.toList() and Stream.mapMulti() (API additions, no JEP)
//
// stream.collect(Collectors.toList()) is the most common terminal operation,
// yet it promises nothing about the mutability of the result. Stream.toList()
// is shorter and always returns an unmodifiable list (which, unlike
// List.copyOf / List.of, may contain nulls).
// mapMulti(BiConsumer<T, Consumer<R>>) is an imperative alternative to
// flatMap: each element pushes zero or more results to a consumer, without
// building a temporary Stream per element. It shines when an element maps to
// few results, or when a type test should filter and cast in one step.
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StreamToListAndMapMulti {
    public static void main(String[] args) {
        // Before: Collectors.toList() (in practice an ArrayList, but not guaranteed)
        List<String> collected = Stream.of("b", "a", "c").collect(Collectors.toList());
        collected.add("d"); // happens to work today
        System.out.println("collect(toList()): " + collected);

        // After: toList() is unmodifiable by contract
        List<String> list = Stream.of("b", "a", "c").sorted().toList();
        System.out.println("toList(): " + list);
        try {
            list.add("d");
        } catch (UnsupportedOperationException e) {
            System.out.println("toList() result is unmodifiable");
        }
        System.out.println("nulls allowed: " + Stream.of("x", null).toList());

        // flatMap: one Stream object per element
        List<Integer> flat = Stream.of(1, 2, 3)
                .flatMap(n -> Stream.of(n, -n))
                .toList();
        // mapMulti: push results directly
        List<Integer> multi = Stream.of(1, 2, 3)
                .<Integer>mapMulti((n, downstream) -> {
                    downstream.accept(n);
                    downstream.accept(-n);
                })
                .toList();
        System.out.println("flatMap:  " + flat);
        System.out.println("mapMulti: " + multi);

        // Filter by type and cast in one step
        List<Object> mixed = List.of(1, "two", 3.0, 4, "five");
        List<Integer> ints = mixed.stream()
                .<Integer>mapMulti((o, downstream) -> {
                    if (o instanceof Integer i) {
                        downstream.accept(i);
                    }
                })
                .toList();
        System.out.println("integers only: " + ints);

        // Recursive expansion of nested lists
        List<Object> nested = List.of(1, List.of(2, List.of(3, 4)), 5);
        List<Object> expanded = nested.stream().mapMulti(StreamToListAndMapMulti::expand).toList();
        System.out.println("flattened: " + expanded);
    }

    private static void expand(Object element, Consumer<Object> downstream) {
        if (element instanceof Iterable<?> iterable) {
            for (Object child : iterable) {
                expand(child, downstream);
            }
        } else {
            downstream.accept(element);
        }
    }
}

/* Output:
collect(toList()): [b, a, c, d]
toList(): [a, b, c]
toList() result is unmodifiable
nulls allowed: [x, null]
flatMap:  [1, -1, 2, -2, 3, -3]
mapMulti: [1, -1, 2, -2, 3, -3]
integers only: [1, 4]
flattened: [1, 2, 3, 4, 5]
*/
