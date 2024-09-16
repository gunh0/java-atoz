// java.util.UUID is a 128-bit identifier, written as 36 characters:
// 8-4-4-4-12 hex digits. Two factory methods create the common versions:
//  - randomUUID(): version 4, 122 random bits from SecureRandom; collisions
//    are practically impossible, so it suits database keys and request ids
//  - nameUUIDFromBytes(bytes): version 3, an MD5 hash of the name; the same
//    name always gives the same UUID (deterministic ids for external keys)
// fromString parses the text form; version() and variant() read the
// metadata bits. A UUID identifies; it is not a secret or a password.
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class UUIDExample {
    static final Pattern CANONICAL =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    public static void main(String[] args) {
        // Random UUIDs: print properties, never the values themselves
        UUID random = UUID.randomUUID();
        System.out.println("random version: " + random.version());
        System.out.println("random variant: " + random.variant() + " (IETF RFC 4122)");
        System.out.println("canonical form: " + CANONICAL.matcher(random.toString()).matches()
                + ", length " + random.toString().length());

        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 100_000; i++) {
            ids.add(UUID.randomUUID());
        }
        System.out.println("100000 random UUIDs, distinct: " + ids.size());

        // Name-based UUIDs: deterministic
        UUID a = UUID.nameUUIDFromBytes("customer:42".getBytes(StandardCharsets.UTF_8));
        UUID b = UUID.nameUUIDFromBytes("customer:42".getBytes(StandardCharsets.UTF_8));
        UUID c = UUID.nameUUIDFromBytes("customer:43".getBytes(StandardCharsets.UTF_8));
        System.out.println("name-based:     " + a);
        System.out.println("version " + a.version() + ", same name equal: " + a.equals(b) + ", other name equal: " + a.equals(c));

        // Parsing and the two 64-bit halves
        UUID parsed = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        System.out.println("parsed version: " + parsed.version() + ", variant: " + parsed.variant());
        System.out.printf("msb=%016x lsb=%016x%n", parsed.getMostSignificantBits(), parsed.getLeastSignificantBits());
        System.out.println("rebuilt equal:  " + new UUID(parsed.getMostSignificantBits(), parsed.getLeastSignificantBits()).equals(parsed));

        // fromString is case-insensitive and always prints lower case
        System.out.println("upper input:    " + UUID.fromString("123E4567-E89B-12D3-A456-426614174000"));

        // Validate untrusted input before using it: fromString rejects garbage...
        for (String input : new String[] {"not-a-uuid", "123e4567-e89b-12d3-a456-42661417400g"}) {
            try {
                UUID.fromString(input);
                System.out.println("accepted: " + input);
            } catch (IllegalArgumentException e) {
                System.out.println("rejected by fromString: " + input);
            }
        }
        // ...but is lenient about short groups, so check the canonical shape too
        String lenient = "1-2-3-4-5";
        System.out.println("fromString(\"" + lenient + "\") = " + UUID.fromString(lenient)
                + ", canonical: " + CANONICAL.matcher(lenient).matches());

        // Nil UUID as a sentinel
        UUID nil = new UUID(0, 0);
        System.out.println("nil: " + nil);
    }
}

/* Output:
random version: 4
random variant: 2 (IETF RFC 4122)
canonical form: true, length 36
100000 random UUIDs, distinct: 100000
name-based:     735a7f40-28d8-396a-abb0-4713244c7577
version 3, same name equal: true, other name equal: false
parsed version: 1, variant: 2
msb=123e4567e89b12d3 lsb=a456426614174000
rebuilt equal:  true
upper input:    123e4567-e89b-12d3-a456-426614174000
rejected by fromString: not-a-uuid
rejected by fromString: 123e4567-e89b-12d3-a456-42661417400g
fromString("1-2-3-4-5") = 00000001-0002-0003-0004-000000000005, canonical: false
nil: 00000000-0000-0000-0000-000000000000
*/
