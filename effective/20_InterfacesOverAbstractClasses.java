// Effective Java, Item 20: Prefer interfaces to abstract classes.
//
// Java has single inheritance of classes, so an abstract class forces
// implementers into one hierarchy, while any class (even an existing one)
// can be retrofitted to implement an interface. Interfaces are ideal for
// mixins (Comparable, AutoCloseable) and for non-hierarchical type
// frameworks (something can be both a Singer and a Songwriter). To share
// code, pair the interface with a skeletal implementation class named
// AbstractXxx (AbstractList, AbstractMap ...): implementers get most methods
// for free but are never forced to extend it.
import java.util.AbstractList;
import java.util.Collections;
import java.util.List;

public class InterfacesOverAbstractClasses {
    public static void main(String[] args) {
        // Mixin interfaces combine freely
        SingerSongwriter iu = new Artist("IU");
        System.out.println(iu.sing("Palette"));
        System.out.println(iu.compose("Love wins all"));
        System.out.println(iu.strum());

        // Skeletal implementation: a full List view over an int[] in a few lines
        int[] scores = {72, 95, 88, 61};
        List<Integer> view = intArrayAsList(scores);
        Collections.sort(view, Collections.reverseOrder());
        System.out.println("sorted through the List view: " + java.util.Arrays.toString(scores));
        System.out.println("contains 88: " + view.contains(88) + ", indexOf 61: " + view.indexOf(61)
                + ", subList: " + view.subList(1, 3));
    }

    // AbstractList supplies iterator, contains, indexOf, subList, equals,
    // hashCode, toString ... on top of get, set and size.
    static List<Integer> intArrayAsList(int[] a) {
        return new AbstractList<>() {
            @Override
            public Integer get(int i) {
                return a[i];
            }

            @Override
            public Integer set(int i, Integer value) {
                int old = a[i];
                a[i] = value;
                return old;
            }

            @Override
            public int size() {
                return a.length;
            }
        };
    }
}

interface Singer {
    String name();

    default String sing(String song) {
        return name() + " sings " + song;
    }
}

interface Songwriter {
    String name();

    default String compose(String song) {
        return name() + " writes " + song;
    }
}

// A third type combining both, with its own additions
interface SingerSongwriter extends Singer, Songwriter {
    default String strum() {
        return name() + " plays guitar";
    }
}

// The class picks its single superclass freely and still gets all three types
final class Artist implements SingerSongwriter {
    private final String name;

    Artist(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }
}

/* Output:
IU sings Palette
IU writes Love wins all
IU plays guitar
sorted through the List view: [95, 88, 72, 61]
contains 88: true, indexOf 61: 3, subList: [88, 72]
*/
