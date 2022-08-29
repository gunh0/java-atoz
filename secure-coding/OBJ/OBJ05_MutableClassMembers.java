// OBJ05-J. Do not return references to private mutable class members
// https://wiki.sei.cmu.edu/confluence/display/java/OBJ05-J.+Do+not+return+references+to+private+mutable+class+members
//
// Declaring a field private protects the reference, not the object it points
// to. A getter that returns a private Date, array or collection hands the
// caller a handle on the internal state, and the caller can change it
// without the class noticing, for example to move an expiry date or add
// itself to an access list. The same hole exists in the other direction when
// a constructor stores a caller's mutable argument. Return (and store)
// defensive copies or unmodifiable views, or better, use immutable types
// such as java.time.Instant and List.copyOf results.
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class OBJ05_MutableClassMembers {
    public static void main(String[] args) {
        Date issued = new Date(1_650_000_000_000L);
        List<String> admins = new ArrayList<>(List.of("alice"));

        TokenNoncompliant bad = new TokenNoncompliant(issued, admins);
        TokenCompliant good = new TokenCompliant(issued, admins);

        // Attack through the arguments the objects were built from
        admins.add("mallory");
        // Attack through the getters
        bad.expires().setTime(Long.MAX_VALUE);
        good.expires().setTime(Long.MAX_VALUE);
        bad.admins().add("eve");
        try {
            good.admins().add("eve");
        } catch (UnsupportedOperationException e) {
            System.out.println("compliant: admins() is read-only");
        }

        System.out.println("noncompliant: expires changed = " + (bad.expires().getTime() == Long.MAX_VALUE)
                + ", admins = " + bad.admins());
        System.out.println("compliant:    expires changed = " + (good.expires().getTime() == Long.MAX_VALUE)
                + ", admins = " + good.admins());
    }
}

class TokenNoncompliant {
    private final Date expires;
    private final List<String> admins;

    TokenNoncompliant(Date expires, List<String> admins) {
        this.expires = expires; // stores the caller's object
        this.admins = admins;
    }

    Date expires() {
        return expires; // returns the internal object
    }

    List<String> admins() {
        return admins;
    }
}

class TokenCompliant {
    private final Date expires;
    private final List<String> admins;

    TokenCompliant(Date expires, List<String> admins) {
        this.expires = new Date(expires.getTime()); // copy in
        this.admins = new ArrayList<>(admins);
    }

    Date expires() {
        return new Date(expires.getTime()); // copy out
    }

    List<String> admins() {
        return Collections.unmodifiableList(admins); // read-only view
    }
}

/* Output:
compliant: admins() is read-only
noncompliant: expires changed = true, admins = [alice, mallory, eve]
compliant:    expires changed = false, admins = [alice]
*/
