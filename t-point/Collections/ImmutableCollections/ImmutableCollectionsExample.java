// Four ways to get a list nobody can change, and they are not the same thing:
//  - Collections.unmodifiableList(list): a read-only VIEW. Writes through the
//    view throw, but whoever holds the original can still change it.
//  - List.copyOf(list) / List.of(...): an independent immutable SNAPSHOT. Nulls
//    are rejected, even contains(null) throws, and copyOf returns its argument
//    unchanged when that is already such a list (no copy needed).
//  - stream.toList(): unmodifiable, but allows nulls;
//    Collectors.toUnmodifiableList() rejects them; Collectors.toList() gives no
//    guarantee at all (today it returns a mutable ArrayList).
//  - Arrays.asList(array): fixed size, not read-only: set() writes to the array.
// All of them are shallow: the elements themselves may still be mutable.
// Set.of / Map.of iterate in an order that changes from run to run: sort first.
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ImmutableCollectionsExample {
    // A record that must not be changed after construction copies its list
    record Team(String name, List<String> members) {
        Team {
            members = List.copyOf(members); // snapshot, and null members are rejected
        }
    }

    static String attempt(Supplier<Object> action) {
        try {
            return "ok -> " + action.get();
        } catch (RuntimeException e) {
            return e.getClass().getSimpleName();
        }
    }

    public static void main(String[] args) {
        List<String> roles = new ArrayList<>(List.of("reader", "writer"));
        List<String> view = Collections.unmodifiableList(roles);
        List<String> snapshot = List.copyOf(roles);

        System.out.println("view.add:     " + attempt(() -> view.add("admin")));
        System.out.println("snapshot.add: " + attempt(() -> snapshot.add("admin")));
        roles.add("admin"); // through the original reference
        System.out.println("after roles.add(\"admin\"): view=" + view + " snapshot=" + snapshot);

        // copyOf skips the copy when it can prove the list is already immutable
        List<String> fixed = List.of("a", "b");
        System.out.println("List.copyOf(List.of(..)) is the same object: " + (List.copyOf(fixed) == fixed));
        System.out.println("List.copyOf(view) is the same object:        " + (List.copyOf(view) == view));

        // Nulls
        List<String> withNull = Arrays.asList("x", null);
        System.out.println("unmodifiableList with null:  " + attempt(() -> Collections.unmodifiableList(withNull)));
        System.out.println("List.copyOf with null:       " + attempt(() -> List.copyOf(withNull)));
        System.out.println("List.of(..).contains(null):  " + attempt(() -> fixed.contains(null)));
        System.out.println("Stream.toList with null:     " + attempt(() -> Stream.of("x", null).toList()));
        System.out.println("toUnmodifiableList w/ null:  "
                + attempt(() -> Stream.of("x", null).collect(Collectors.toUnmodifiableList())));

        // What each collector returns when you try to add
        List<String> collected = Stream.of("p", "q").collect(Collectors.toList());
        List<String> streamed = Stream.of("p", "q").toList();
        System.out.println("Collectors.toList().add: " + attempt(() -> collected.add("r")));
        System.out.println("Stream.toList().add:     " + attempt(() -> streamed.add("r")));

        // Arrays.asList: fixed size, but writable and backed by the array
        String[] backing = {"red", "green", "blue"};
        List<String> asList = Arrays.asList(backing);
        System.out.println("asList.set:  " + attempt(() -> asList.set(0, "RED")) + ", array[0] is now " + backing[0]);
        System.out.println("asList.add:  " + attempt(() -> asList.add("pink")));

        // Shallow: the list cannot change, the StringBuilder inside can
        List<StringBuilder> builders = List.of(new StringBuilder("draft"));
        builders.get(0).append(" (edited)");
        System.out.println("element of an immutable list changed: " + builders);

        // Set.of and Map.of reject duplicates instead of silently keeping one
        System.out.println("Set.of(\"a\", \"a\"):        " + attempt(() -> Set.of("a", "a")));
        System.out.println("Map.of(\"k\", 1, \"k\", 2):  " + attempt(() -> Map.of("k", 1, "k", 2)));
        Set<String> scopes = Set.of("write", "read", "admin", "delete");
        Map<String, Integer> limits = Map.ofEntries(Map.entry("free", 10), Map.entry("pro", 1000), Map.entry("team", 500));
        System.out.println("sorted Set.of:  " + new TreeSet<>(scopes));
        System.out.println("sorted Map.of:  " + new TreeMap<>(limits));

        // The record's list is a snapshot of the caller's list
        List<String> members = new ArrayList<>(List.of("ann", "bob"));
        Team team = new Team("blue", members);
        members.clear();
        System.out.println(team + ", members.add: " + attempt(() -> team.members().add("eve")));
        System.out.println("Team with a null member: " + attempt(() -> new Team("red", Arrays.asList("ann", null))));
    }
}

/* Output:
view.add:     UnsupportedOperationException
snapshot.add: UnsupportedOperationException
after roles.add("admin"): view=[reader, writer, admin] snapshot=[reader, writer]
List.copyOf(List.of(..)) is the same object: true
List.copyOf(view) is the same object:        false
unmodifiableList with null:  ok -> [x, null]
List.copyOf with null:       NullPointerException
List.of(..).contains(null):  NullPointerException
Stream.toList with null:     ok -> [x, null]
toUnmodifiableList w/ null:  NullPointerException
Collectors.toList().add: ok -> true
Stream.toList().add:     UnsupportedOperationException
asList.set:  ok -> red, array[0] is now RED
asList.add:  UnsupportedOperationException
element of an immutable list changed: [draft (edited)]
Set.of("a", "a"):        IllegalArgumentException
Map.of("k", 1, "k", 2):  IllegalArgumentException
sorted Set.of:  [admin, delete, read, write]
sorted Map.of:  {free=10, pro=1000, team=500}
Team[name=blue, members=[ann, bob]], members.add: UnsupportedOperationException
Team with a null member: NullPointerException
*/
