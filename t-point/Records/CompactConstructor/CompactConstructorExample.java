// A record's canonical constructor can be written in compact form: no
// parameter list and no field assignments. The body runs first, then the
// (possibly reassigned) parameters are copied into the final fields.
// That makes it the place to:
//  - validate arguments and reject invalid state (records cannot be
//    constructed any other way, so the invariant always holds)
//  - normalize values (trim, lower-case, canonical units)
//  - make defensive copies of mutable components (List.copyOf, clone)
// Additional constructors must delegate to the canonical one with this(...),
// so they get the same checks for free.
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CompactConstructorExample {

    record Port(int number) {
        Port {
            if (number < 0 || number > 65_535) {
                throw new IllegalArgumentException("port out of range: " + number);
            }
        }
    }

    record Email(String address) {
        Email {
            Objects.requireNonNull(address, "address");
            address = address.strip().toLowerCase(java.util.Locale.ROOT); // normalize
            if (!address.matches("[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}")) {
                throw new IllegalArgumentException("not an email address: " + address);
            }
        }
    }

    record Range(int from, int to) {
        Range {
            if (from > to) {
                throw new IllegalArgumentException("from > to: " + from + " > " + to);
            }
        }

        // A non-canonical constructor must call the canonical one first
        Range(int single) {
            this(single, single);
        }

        int length() {
            return to - from + 1;
        }
    }

    record Team(String name, List<String> members) {
        Team {
            members = List.copyOf(members); // defensive, unmodifiable copy (also rejects nulls)
        }
    }

    // Arrays are mutable too: copy on the way in and on the way out
    record Digest(String algorithm, byte[] value) {
        Digest {
            value = value.clone();
        }

        @Override
        public byte[] value() {
            return value.clone();
        }
    }

    public static void main(String[] args) {
        System.out.println(new Port(8080));
        attempt("Port(70000)", () -> new Port(70_000));

        System.out.println(new Email("  Alice@Example.COM "));
        attempt("Email(\"not-an-email\")", () -> new Email("not-an-email"));
        attempt("Email(null)", () -> new Email(null));

        System.out.println(new Range(3, 7) + " length " + new Range(3, 7).length());
        System.out.println(new Range(5) + " length " + new Range(5).length());
        attempt("Range(9, 1)", () -> new Range(9, 1));

        List<String> source = new ArrayList<>(List.of("alice", "bob"));
        Team team = new Team("blue", source);
        source.add("mallory"); // does not leak into the record
        System.out.println(team);
        attempt("team.members().add(\"eve\")", () -> team.members().add("eve"));

        byte[] bytes = {1, 2, 3};
        Digest digest = new Digest("SHA-256", bytes);
        bytes[0] = 99;              // caller's array changed after construction
        digest.value()[1] = 99;     // caller changes the returned copy
        System.out.println("digest bytes: " + java.util.Arrays.toString(digest.value()));
    }

    static void attempt(String label, Runnable action) {
        try {
            action.run();
            System.out.println(label + " -> ok");
        } catch (IllegalArgumentException e) {
            System.out.println(label + " -> IllegalArgumentException: " + e.getMessage());
        } catch (RuntimeException e) {
            System.out.println(label + " -> " + e.getClass().getSimpleName());
        }
    }
}

/* Output:
Port[number=8080]
Port(70000) -> IllegalArgumentException: port out of range: 70000
Email[address=alice@example.com]
Email("not-an-email") -> IllegalArgumentException: not an email address: not-an-email
Email(null) -> NullPointerException
Range[from=3, to=7] length 5
Range[from=5, to=5] length 1
Range(9, 1) -> IllegalArgumentException: from > to: 9 > 1
Team[name=blue, members=[alice, bob]]
team.members().add("eve") -> UnsupportedOperationException
digest bytes: [1, 2, 3]
*/
