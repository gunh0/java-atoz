// Effective Java, Item 52: Use overloading judiciously.
//
// The overload to call is chosen at COMPILE time from the static types of
// the arguments, while an override is chosen at RUN time from the receiver's
// dynamic type. Code that expects overloads to behave like overrides
// silently does the wrong thing. Safe policy: never export two overloads
// with the same number of parameters (give them different names, like
// writeInt / writeLong), and never overload a method to take different
// functional interfaces in the same position. Autoboxing and generics made
// this worse: List<Integer> has remove(int index) and remove(Object o).
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class OverloadingJudiciously {
    public static void main(String[] args) {
        Collection<?>[] collections = { new HashSet<String>(), new ArrayList<BigInteger>(),
                new HashMap<String, String>().values() };

        // Overloading: the static type is always Collection<?>, so the same overload runs
        for (Collection<?> c : collections) {
            System.out.println("overloaded classify: " + CollectionClassifier.classify(c));
        }
        // One method with explicit instanceof tests does what was intended
        for (Collection<?> c : collections) {
            System.out.println("instanceof classify: " + CollectionClassifier.classifyFixed(c));
        }

        // Overriding, by contrast, follows the run-time type
        for (Wine wine : List.of(new Wine(), new SparklingWine(), new Champagne())) {
            System.out.println("overridden name: " + wine.name());
        }

        // The remove(int) / remove(Object) trap
        Set<Integer> set = new TreeSet<>();
        List<Integer> list = new ArrayList<>();
        for (int i = -3; i < 3; i++) {
            set.add(i);
            list.add(i);
        }
        for (int i = 0; i < 3; i++) {
            set.remove(i);  // remove(Object): boxes i, removes the value
            list.remove(i); // remove(int): removes the element at position i
        }
        System.out.println("set after remove(0..2):  " + set);
        System.out.println("list after remove(0..2): " + list);

        List<Integer> fixed = new ArrayList<>(List.of(-3, -2, -1, 0, 1, 2));
        for (int i = 0; i < 3; i++) {
            fixed.remove((Integer) i); // force the remove(Object) overload
        }
        System.out.println("list after remove((Integer) i): " + fixed);
    }
}

class CollectionClassifier {
    static String classify(Set<?> s) {
        return "Set";
    }

    static String classify(List<?> lst) {
        return "List";
    }

    static String classify(Collection<?> c) {
        return "Unknown Collection";
    }

    static String classifyFixed(Collection<?> c) {
        return c instanceof Set ? "Set" : c instanceof List ? "List" : "Unknown Collection";
    }
}

class Wine {
    String name() {
        return "wine";
    }
}

class SparklingWine extends Wine {
    @Override
    String name() {
        return "sparkling wine";
    }
}

class Champagne extends SparklingWine {
    @Override
    String name() {
        return "champagne";
    }
}

/* Output:
overloaded classify: Unknown Collection
overloaded classify: Unknown Collection
overloaded classify: Unknown Collection
instanceof classify: Set
instanceof classify: List
instanceof classify: Unknown Collection
overridden name: wine
overridden name: sparkling wine
overridden name: champagne
set after remove(0..2):  [-3, -2, -1]
list after remove(0..2): [-2, 0, 2]
list after remove((Integer) i): [-3, -2, -1]
*/
