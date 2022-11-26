// A generic class declares TYPE PARAMETERS (<T>, <K, V>, ...) that callers fill
// in with concrete types: Box<String>, Pair<String, Integer>. The compiler then
// checks every use, so wrong types are compile errors instead of
// ClassCastExceptions at run time, and no casts are needed when reading.
//  - the diamond <> lets the compiler infer the arguments on `new`
//  - type arguments must be reference types: Box<Integer>, not Box<int>
//  - generics are erased at run time: Box<String> and Box<Integer> share one
//    Class object, so `new T()` and `instanceof Box<String>` are not allowed
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class GenericClassExample {
    public static void main(String[] args) {
        // Before generics: Object everywhere and a cast on every read
        ObjectBox raw = new ObjectBox("hello");
        try {
            Integer n = (Integer) raw.get(); // compiles, fails at run time
            System.out.println(n);
        } catch (ClassCastException e) {
            System.out.println("ObjectBox: ClassCastException at run time");
        }

        // With generics: the type travels with the variable
        Box<String> greeting = new Box<>("hello");
        String s = greeting.get(); // no cast
        System.out.println("Box<String>: " + s.toUpperCase());
        // Integer wrong = greeting.get(); // would not compile

        Box<Integer> length = greeting.map(String::length);
        System.out.println("mapped to Box<Integer>: " + length.get());

        // Two type parameters
        Pair<String, Integer> entry = new Pair<>("port", 8080);
        System.out.println(entry + ", swapped: " + entry.swap());

        // A small generic container with its own invariant
        Stack<String> undo = new Stack<>();
        undo.push("type 'a'");
        undo.push("type 'b'");
        System.out.println("undo: " + undo.pop() + ", then " + undo.pop() + ", empty=" + undo.isEmpty());

        // Erasure: one runtime class for every parameterization
        System.out.println("same runtime class: " + (greeting.getClass() == length.getClass()));
    }
}

class ObjectBox {
    private final Object value;

    ObjectBox(Object value) {
        this.value = value;
    }

    Object get() {
        return value;
    }
}

class Box<T> {
    private final T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }

    // Methods of a generic class can introduce further type parameters (R)
    <R> Box<R> map(Function<? super T, ? extends R> f) {
        return new Box<>(f.apply(value));
    }
}

class Pair<A, B> {
    private final A first;
    private final B second;

    Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }

    Pair<B, A> swap() {
        return new Pair<>(second, first);
    }

    @Override
    public String toString() {
        return "(" + first + ", " + second + ")";
    }
}

class Stack<E> {
    private final List<E> items = new ArrayList<>();

    void push(E item) {
        items.add(item);
    }

    E pop() {
        if (items.isEmpty()) throw new IllegalStateException("empty stack");
        return items.remove(items.size() - 1);
    }

    boolean isEmpty() {
        return items.isEmpty();
    }
}

/* Output:
ObjectBox: ClassCastException at run time
Box<String>: HELLO
mapped to Box<Integer>: 5
(port, 8080), swapped: (8080, port)
undo: type 'b', then type 'a', empty=true
same runtime class: true
*/
