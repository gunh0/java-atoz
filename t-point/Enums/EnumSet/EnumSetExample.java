// EnumSet is the Set implementation for enum constants. Internally it is a
// bit vector (one long for enums with up to 64 constants), so contains/add
// are single bit operations and containsAll/retainAll are bitwise AND/OR.
// It replaces the C-style "int flags" idiom (READ = 1, WRITE = 2, ...) while
// staying type-safe and printable.
//  - factories: of, noneOf, allOf, range, complementOf, copyOf
//  - iteration is always in declaration order, never insertion order
//  - null elements are rejected
//  - not thread-safe; wrap with Collections.synchronizedSet if shared
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class EnumSetExample {
    enum Permission { READ, WRITE, EXECUTE, DELETE, SHARE, ADMIN }

    enum Day { MON, TUE, WED, THU, FRI, SAT, SUN }

    record Role(String name, Set<Permission> permissions) {}

    // Union of all permissions granted by a user's roles
    static Set<Permission> effective(Collection<Role> roles) {
        EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
        for (Role r : roles) result.addAll(r.permissions());
        return result;
    }

    public static void main(String[] args) {
        Role viewer = new Role("viewer", EnumSet.of(Permission.READ));
        Role editor = new Role("editor", EnumSet.of(Permission.READ, Permission.WRITE, Permission.SHARE));
        Role owner = new Role("owner", EnumSet.complementOf(EnumSet.of(Permission.ADMIN)));
        Role admin = new Role("admin", EnumSet.allOf(Permission.class));

        for (Role r : List.of(viewer, editor, owner, admin)) {
            System.out.printf("%-6s %s%n", r.name(), r.permissions());
        }

        // Declaration order, regardless of the order elements were added
        EnumSet<Permission> added = EnumSet.noneOf(Permission.class);
        added.add(Permission.SHARE);
        added.add(Permission.READ);
        added.add(Permission.DELETE);
        System.out.println("added SHARE, READ, DELETE -> " + added);

        Set<Permission> alice = effective(List.of(viewer, editor));
        System.out.println("alice (viewer+editor) = " + alice);
        Set<Permission> needed = EnumSet.of(Permission.WRITE, Permission.DELETE);
        System.out.println("alice can WRITE and DELETE: " + alice.containsAll(needed));

        // Set algebra: what is missing?
        EnumSet<Permission> missing = EnumSet.copyOf(needed);
        missing.removeAll(alice);
        System.out.println("missing for that: " + missing);

        // range(): a contiguous slice of the declaration order
        System.out.println("weekdays = " + EnumSet.range(Day.MON, Day.FRI));
        System.out.println("weekend  = " + EnumSet.complementOf(EnumSet.range(Day.MON, Day.FRI)));

        try {
            EnumSet.of(Permission.READ).add(null);
        } catch (NullPointerException e) {
            System.out.println("add(null) -> NullPointerException");
        }

        // The int-flags idiom this replaces, for comparison
        int flags = 1 << Permission.READ.ordinal() | 1 << Permission.WRITE.ordinal();
        System.out.println("int flags READ|WRITE = " + flags + " (meaningless when printed)");
    }
}

/* Output:
viewer [READ]
editor [READ, WRITE, SHARE]
owner  [READ, WRITE, EXECUTE, DELETE, SHARE]
admin  [READ, WRITE, EXECUTE, DELETE, SHARE, ADMIN]
added SHARE, READ, DELETE -> [READ, DELETE, SHARE]
alice (viewer+editor) = [READ, WRITE, SHARE]
alice can WRITE and DELETE: false
missing for that: [DELETE]
weekdays = [MON, TUE, WED, THU, FRI]
weekend  = [SAT, SUN]
add(null) -> NullPointerException
int flags READ|WRITE = 3 (meaningless when printed)
*/
