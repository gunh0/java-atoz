// Effective Java, Item 31: Use bounded wildcards to increase API flexibility.
//
// Because generics are invariant, a method taking Iterable<Number> rejects an
// Iterable<Integer>, even though every Integer is a Number. Wildcards fix
// this. Mnemonic PECS, "producer-extends, consumer-super": a parameter that
// PRODUCES Es for you is Xxx<? extends E>; one that CONSUMES your Es is
// Xxx<? super E>. Comparables and Comparators always consume, so use
// Comparable<? super T>. Wildcards belong on parameters, not on return types,
// and if a type parameter appears only once in a signature, a wildcard
// (with a private capture helper when needed) is usually cleaner.
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BoundedWildcards {
    public static void main(String[] args) {
        Stack<Number> numberStack = new Stack<>();

        // Producer: pushAll(Iterable<? extends E>) accepts Integers and Doubles
        List<Integer> integers = List.of(1, 2, 3);
        List<Double> doubles = List.of(4.5);
        numberStack.pushAll(integers);
        numberStack.pushAll(doubles);

        // Consumer: popAll(Collection<? super E>) can fill a List<Object>
        List<Object> objects = new ArrayList<>();
        numberStack.popAll(objects);
        System.out.println("popped into List<Object>: " + objects);

        // Comparable<? super T>: Apple is Comparable<Fruit>, not Comparable<Apple>
        List<Apple> apples = List.of(new Apple("fuji", 180), new Apple("gala", 150), new Apple("honeycrisp", 220));
        System.out.println("heaviest apple: " + max(apples));

        // Wildcard in the API, type parameter in a private helper
        List<String> words = new ArrayList<>(List.of("first", "second", "third"));
        swap(words, 0, 2);
        System.out.println("swapped: " + words);
    }

    static <T extends Comparable<? super T>> T max(List<? extends T> list) {
        T result = list.get(0);
        for (T t : list) {
            if (t.compareTo(result) > 0) {
                result = t;
            }
        }
        return result;
    }

    // Public API: simplest signature, one unbounded wildcard
    static void swap(List<?> list, int i, int j) {
        swapHelper(list, i, j);
    }

    // Captures the wildcard so we can put elements back into the list
    private static <E> void swapHelper(List<E> list, int i, int j) {
        list.set(i, list.set(j, list.get(i)));
    }
}

class Stack<E> {
    private final List<E> elements = new ArrayList<>();

    void push(E e) {
        elements.add(e);
    }

    E pop() {
        return elements.remove(elements.size() - 1);
    }

    boolean isEmpty() {
        return elements.isEmpty();
    }

    void pushAll(Iterable<? extends E> src) {
        for (E e : src) {
            push(e);
        }
    }

    void popAll(Collection<? super E> dst) {
        while (!isEmpty()) {
            dst.add(pop());
        }
    }
}

abstract class Fruit implements Comparable<Fruit> {
    final String name;
    final int grams;

    Fruit(String name, int grams) {
        this.name = name;
        this.grams = grams;
    }

    @Override
    public int compareTo(Fruit other) {
        return Integer.compare(grams, other.grams);
    }

    @Override
    public String toString() {
        return name + " (" + grams + " g)";
    }
}

class Apple extends Fruit {
    Apple(String name, int grams) {
        super(name, grams);
    }
}

/* Output:
popped into List<Object>: [4.5, 3, 2, 1]
heaviest apple: honeycrisp (220 g)
swapped: [third, second, first]
*/
