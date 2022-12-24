// Effective Java, Item 37: Use EnumMap instead of ordinal indexing.
//
// Indexing an array by ordinal() to associate data with enum constants
// compiles into fragile code: the array is unrelated to the enum type, needs
// an unchecked cast for generic elements, prints as nothing useful, and a
// wrong int index fails only at run time (or worse, silently). EnumMap is a
// Map keyed by an enum, implemented internally as an ordinal-indexed array,
// so it is just as fast but typesafe and readable. With streams, pass
// EnumMap::new as the map factory to groupingBy. For two-dimensional data
// (from-phase, to-phase) nest them: EnumMap<..., EnumMap<..., ...>>.
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EnumMapDemo {
    public static void main(String[] args) {
        List<Plant> garden = List.of(
                new Plant("basil", Plant.LifeCycle.ANNUAL),
                new Plant("carroway", Plant.LifeCycle.BIENNIAL),
                new Plant("dill", Plant.LifeCycle.ANNUAL),
                new Plant("lavendar", Plant.LifeCycle.PERENNIAL),
                new Plant("parsley", Plant.LifeCycle.BIENNIAL),
                new Plant("rosemary", Plant.LifeCycle.PERENNIAL));

        // Ordinal indexing: works, but nothing ties index i to a LifeCycle
        @SuppressWarnings("unchecked")
        Set<Plant>[] byOrdinal = (Set<Plant>[]) new Set[Plant.LifeCycle.values().length];
        for (int i = 0; i < byOrdinal.length; i++) {
            byOrdinal[i] = new TreeSet<>();
        }
        for (Plant p : garden) {
            byOrdinal[p.lifeCycle.ordinal()].add(p);
        }
        for (int i = 0; i < byOrdinal.length; i++) {
            System.out.printf("ordinal %d: %s%n", i, byOrdinal[i]); // label by hand
        }

        // EnumMap: the key IS the label
        Map<Plant.LifeCycle, Set<Plant>> byLifeCycle = new EnumMap<>(Plant.LifeCycle.class);
        for (Plant.LifeCycle lc : Plant.LifeCycle.values()) {
            byLifeCycle.put(lc, new TreeSet<>());
        }
        for (Plant p : garden) {
            byLifeCycle.get(p.lifeCycle).add(p);
        }
        System.out.println("EnumMap: " + byLifeCycle);

        // Streams: ask groupingBy for an EnumMap
        Map<Plant.LifeCycle, List<String>> grouped = garden.stream()
                .filter(p -> p.lifeCycle != Plant.LifeCycle.BIENNIAL)
                .collect(Collectors.groupingBy(p -> p.lifeCycle,
                        () -> new EnumMap<>(Plant.LifeCycle.class),
                        Collectors.mapping(p -> p.name, Collectors.toList())));
        System.out.println("groupingBy into EnumMap: " + grouped + " (" + grouped.getClass().getSimpleName() + ")");

        // Nested EnumMap for a two-dimensional relation
        System.out.println("SOLID -> LIQUID: " + Phase.Transition.from(Phase.SOLID, Phase.LIQUID));
        System.out.println("GAS -> SOLID: " + Phase.Transition.from(Phase.GAS, Phase.SOLID));
    }
}

class Plant implements Comparable<Plant> {
    enum LifeCycle { ANNUAL, PERENNIAL, BIENNIAL }

    final String name;
    final LifeCycle lifeCycle;

    Plant(String name, LifeCycle lifeCycle) {
        this.name = name;
        this.lifeCycle = lifeCycle;
    }

    @Override
    public int compareTo(Plant o) {
        return name.compareTo(o.name);
    }

    @Override
    public String toString() {
        return name;
    }
}

enum Phase {
    SOLID, LIQUID, GAS;

    enum Transition {
        MELT(SOLID, LIQUID), FREEZE(LIQUID, SOLID),
        BOIL(LIQUID, GAS), CONDENSE(GAS, LIQUID),
        SUBLIME(SOLID, GAS), DEPOSIT(GAS, SOLID);

        private final Phase from;
        private final Phase to;

        Transition(Phase from, Phase to) {
            this.from = from;
            this.to = to;
        }

        // from-phase -> (to-phase -> transition); adding a constant needs no table edits
        private static final Map<Phase, Map<Phase, Transition>> TABLE = Stream.of(values())
                .collect(Collectors.groupingBy(t -> t.from, () -> new EnumMap<>(Phase.class),
                        Collectors.toMap(t -> t.to, t -> t, (x, y) -> y, () -> new EnumMap<>(Phase.class))));

        static Transition from(Phase from, Phase to) {
            return TABLE.get(from).get(to);
        }
    }
}

/* Output:
ordinal 0: [basil, dill]
ordinal 1: [lavendar, rosemary]
ordinal 2: [carroway, parsley]
EnumMap: {ANNUAL=[basil, dill], PERENNIAL=[lavendar, rosemary], BIENNIAL=[carroway, parsley]}
groupingBy into EnumMap: {ANNUAL=[basil, dill], PERENNIAL=[lavendar, rosemary]} (EnumMap)
SOLID -> LIQUID: MELT
GAS -> SOLID: DEPOSIT
*/
