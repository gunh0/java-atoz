// Effective Java, Item 28: Prefer lists to arrays.
//
// Arrays are covariant (a Long[] IS an Object[]) and reified (they check
// their element type at run time). Generics are invariant (a List<Long> is
// NOT a List<Object>) and erased (the element type is checked at compile
// time only). So putting a String into a Long[] viewed as Object[] compiles
// and throws ArrayStoreException at run time, while the List version simply
// does not compile. The two also mix badly: generic array creation such as
// new List<String>[3] or new E[n] is illegal. When you get errors or
// warnings mixing them, replace the arrays with lists.
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

public class ListsOverArrays {
    public static void main(String[] args) {
        // Covariant arrays: the mistake surfaces at run time
        Object[] objects = new Long[1];
        try {
            objects[0] = "I don't fit in";
        } catch (ArrayStoreException e) {
            System.out.println("array: ArrayStoreException: " + e.getMessage());
        }

        // Invariant generics: the same mistake is a compile-time error
        // List<Object> ol = new ArrayList<Long>(); // incompatible types
        List<Long> longs = new ArrayList<>();
        longs.add(1L);
        System.out.println("list: compile-time checked " + longs);

        // Array-based chooser needs a cast and can fail at run time
        List<Object> mixed = List.of("rock", "paper", 3);
        ArrayChooser arrayChooser = new ArrayChooser(mixed);
        try {
            for (int i = 0; i < 6; i++) {
                String s = (String) arrayChooser.choose(); // client-side cast
                System.out.println("array chooser picked " + s);
            }
        } catch (ClassCastException e) {
            System.out.println("array chooser: ClassCastException in client code");
        }

        // List-based chooser: typesafe, no casts
        Chooser<String> chooser = new Chooser<>(List.of("rock", "paper", "scissors"), new Random(42));
        StringBuilder picks = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            picks.append(chooser.choose()).append(' ');
        }
        System.out.println("list chooser: " + picks.toString().trim());
    }
}

class ArrayChooser {
    private final Object[] choiceArray;
    private final Random rnd = new Random(7);

    ArrayChooser(Collection<?> choices) {
        choiceArray = choices.toArray();
    }

    Object choose() {
        return choiceArray[rnd.nextInt(choiceArray.length)];
    }
}

class Chooser<T> {
    private final List<T> choiceList;
    private final Random rnd;

    Chooser(Collection<T> choices, Random rnd) {
        this.choiceList = new ArrayList<>(choices); // defensive copy, typesafe
        this.rnd = rnd;
    }

    T choose() {
        return choiceList.get(rnd.nextInt(choiceList.size()));
    }
}

/* Output:
array: ArrayStoreException: java.lang.String
list: compile-time checked [1]
array chooser picked paper
array chooser: ClassCastException in client code
list chooser: scissors rock rock scissors rock
*/
