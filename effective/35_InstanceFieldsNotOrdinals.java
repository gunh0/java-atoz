// Effective Java, Item 35: Use instance fields instead of ordinals.
//
// Every enum constant has an ordinal(), its position in the declaration.
// Deriving a value from it (ordinal() + 1 musicians) ties the value to the
// declaration order: reordering the constants silently changes the result,
// two constants can never share a value, and a gap (no 11-musician ensemble)
// cannot be expressed without dummy constants. Store the value in an
// instance field instead. ordinal() exists for general-purpose structures
// such as EnumSet and EnumMap, not for application code.
public class InstanceFieldsNotOrdinals {
    // Abuse: the size is derived from the position
    enum OrdinalEnsemble {
        SOLO, DUET, TRIO, QUARTET, QUINTET;

        int numberOfMusicians() {
            return ordinal() + 1;
        }
    }

    // Same constants after someone sorted them alphabetically
    enum SortedOrdinalEnsemble {
        DUET, QUARTET, QUINTET, SOLO, TRIO;

        int numberOfMusicians() {
            return ordinal() + 1;
        }
    }

    // Recommended: an explicit instance field
    enum Ensemble {
        SOLO(1), DUET(2), TRIO(3), QUARTET(4), QUINTET(5),
        SEXTET(6), SEPTET(7), OCTET(8), DOUBLE_QUARTET(8),
        NONET(9), DECTET(10), TRIPLE_QUARTET(12);

        private final int numberOfMusicians;

        Ensemble(int size) {
            this.numberOfMusicians = size;
        }

        int numberOfMusicians() {
            return numberOfMusicians;
        }
    }

    public static void main(String[] args) {
        System.out.println("ordinal-based TRIO:          " + OrdinalEnsemble.TRIO.numberOfMusicians());
        System.out.println("after reordering, TRIO:      " + SortedOrdinalEnsemble.TRIO.numberOfMusicians());
        System.out.println("field-based TRIO:            " + Ensemble.TRIO.numberOfMusicians());
        System.out.println("shared value: OCTET=" + Ensemble.OCTET.numberOfMusicians()
                + ", DOUBLE_QUARTET=" + Ensemble.DOUBLE_QUARTET.numberOfMusicians());
        System.out.println("gap allowed: TRIPLE_QUARTET=" + Ensemble.TRIPLE_QUARTET.numberOfMusicians()
                + " right after DECTET=" + Ensemble.DECTET.numberOfMusicians());
    }
}

/* Output:
ordinal-based TRIO:          3
after reordering, TRIO:      5
field-based TRIO:            3
shared value: OCTET=8, DOUBLE_QUARTET=8
gap allowed: TRIPLE_QUARTET=12 right after DECTET=10
*/
