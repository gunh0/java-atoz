// Effective Java, Item 36: Use EnumSet instead of bit fields.
//
// Packing int enum constants into a bit field (STYLE_BOLD | STYLE_ITALIC)
// gives fast set operations but keeps every flaw of int constants: no type
// safety, a printed value of "3" means nothing, iterating the members needs
// bit twiddling, and you must guess the width (int or long) up front.
// EnumSet gives the same performance (it is a single long internally for
// enums with up to 64 constants) with a real Set interface, type safety,
// readable toString and handy factories like of, range and complementOf.
// Accept the Set interface in APIs; EnumSet is the implementation.
import java.util.EnumSet;
import java.util.Set;

public class EnumSetDemo {
    // Bit field enumeration constants: obsolete
    static final int STYLE_BOLD = 1 << 0;
    static final int STYLE_ITALIC = 1 << 1;
    static final int STYLE_UNDERLINE = 1 << 2;
    static final int STYLE_STRIKETHROUGH = 1 << 3;

    enum Style { BOLD, ITALIC, UNDERLINE, STRIKETHROUGH }

    enum Permission { READ, WRITE, EXECUTE, DELETE, ADMIN }

    public static void main(String[] args) {
        // Bit field: opaque number, any int is accepted
        int bits = STYLE_BOLD | STYLE_ITALIC;
        System.out.println("bit field: " + bits + " (and applyStylesBits(99) compiles too)");
        System.out.println("bold set? " + ((bits & STYLE_BOLD) != 0));

        // EnumSet: readable, typesafe, still a bit vector underneath
        Set<Style> styles = EnumSet.of(Style.BOLD, Style.ITALIC);
        applyStyles(styles);
        System.out.println("bold set? " + styles.contains(Style.BOLD));

        // Set algebra
        Set<Permission> user = EnumSet.of(Permission.READ, Permission.WRITE);
        Set<Permission> nonAdmin = EnumSet.range(Permission.READ, Permission.DELETE);
        Set<Permission> denied = EnumSet.complementOf(EnumSet.copyOf(user));
        System.out.println("user: " + user + ", denied: " + denied);
        System.out.println("everything but ADMIN: " + nonAdmin);
        System.out.println("all: " + EnumSet.allOf(Permission.class) + ", none: " + EnumSet.noneOf(Permission.class));

        // Iteration order is always declaration order
        System.out.println("iteration order: " + EnumSet.of(Style.STRIKETHROUGH, Style.BOLD, Style.UNDERLINE));
    }

    // Any Set works, EnumSet is just the best implementation
    static void applyStyles(Set<Style> styles) {
        System.out.println("applying " + styles);
    }
}

/* Output:
bit field: 3 (and applyStylesBits(99) compiles too)
bold set? true
applying [BOLD, ITALIC]
bold set? true
user: [READ, WRITE], denied: [EXECUTE, DELETE, ADMIN]
everything but ADMIN: [READ, WRITE, EXECUTE, DELETE]
all: [READ, WRITE, EXECUTE, DELETE, ADMIN], none: []
iteration order: [BOLD, UNDERLINE, STRIKETHROUGH]
*/
