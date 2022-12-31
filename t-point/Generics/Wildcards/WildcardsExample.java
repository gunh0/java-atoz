// Generics are INVARIANT: a List<Integer> is not a List<Number>, even though an
// Integer is a Number (otherwise you could add a Double to a list of Integers).
// Wildcards relax this at the use site:
//  - List<? extends Number>: "some list of a Number subtype". You can READ
//    Numbers from it but not add (except null). It is a PRODUCER.
//  - List<? super Integer>: "some list that can hold Integers". You can ADD
//    Integers to it, but reads only give Object. It is a CONSUMER.
//  - List<?>: any list, when only the element-independent methods are needed.
// Mnemonic: PECS, Producer Extends, Consumer Super (see Collections.copy).
import java.util.ArrayList;
import java.util.List;

public class WildcardsExample {
    public static void main(String[] args) {
        List<Integer> ints = List.of(1, 2, 3);
        List<Double> doubles = List.of(0.5, 1.5);

        // Invariance: this does not compile
        // List<Number> numbers = ints;

        // ? extends: one method sums any list of numbers
        System.out.println("sum(ints) = " + sum(ints));
        System.out.println("sum(doubles) = " + sum(doubles));

        // ? super: one method fills a list of Integer, Number or Object
        List<Integer> intSink = new ArrayList<>();
        List<Number> numberSink = new ArrayList<>();
        List<Object> objectSink = new ArrayList<>();
        addSequence(intSink, 3);
        addSequence(numberSink, 3);
        addSequence(objectSink, 3);
        numberSink.add(2.5); // still a List<Number> for its owner
        System.out.println("intSink=" + intSink + ", numberSink=" + numberSink + ", objectSink=" + objectSink);

        // Both at once: copy from a producer into a consumer
        List<Number> all = new ArrayList<>();
        copy(ints, all);
        copy(doubles, all);
        System.out.println("copied: " + all);

        // ?: only element-type-independent operations
        System.out.println("sizes: " + describe(ints) + ", " + describe(List.of("a", "b")));
    }

    // Producer: we only read Numbers out
    static double sum(List<? extends Number> numbers) {
        double total = 0;
        for (Number n : numbers) {
            total += n.doubleValue();
        }
        // numbers.add(1); // compile error: the real element type is unknown
        return total;
    }

    // Consumer: we only put Integers in
    static void addSequence(List<? super Integer> sink, int count) {
        for (int i = 1; i <= count; i++) {
            sink.add(i);
        }
        Object first = sink.get(0); // reading gives only Object
    }

    static <T> void copy(List<? extends T> source, List<? super T> target) {
        for (T item : source) {
            target.add(item);
        }
    }

    static String describe(List<?> list) {
        return list.size() + (list.isEmpty() ? " (empty)" : " items");
    }
}

/* Output:
sum(ints) = 6.0
sum(doubles) = 2.0
intSink=[1, 2, 3], numberSink=[1, 2, 3, 2.5], objectSink=[1, 2, 3]
copied: [1, 2, 3, 0.5, 1.5]
sizes: 3 items, 2 items
*/
