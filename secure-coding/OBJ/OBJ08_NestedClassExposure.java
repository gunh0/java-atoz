// OBJ08-J. Do not expose private members of an outer class from within a nested class
// https://wiki.sei.cmu.edu/confluence/display/java/OBJ08-J.+Do+not+expose+private+members+of+an+outer+class+from+within+a+nested+class
//
// A nested class can read every private member of its enclosing class: the
// two are nestmates and share one access boundary. That is fine as long as
// the nested class is an implementation detail. If the nested class is
// public (or protected or package-private and reachable by untrusted code)
// and one of its methods returns or prints outer private state, then
// "private" on the outer field means nothing: anybody can create the
// nested object and read through it. Keep such nested classes private, and
// let them expose only what the outer class itself would be willing to
// expose.
import java.lang.reflect.Modifier;

public class OBJ08_NestedClassExposure {
    public static void main(String[] args) {
        System.out.println("untrusted code, noncompliant: " + UntrustedPlugin.peek(new ApiCredentialsNoncompliant("sk_live_51Hx9")));
        System.out.println("untrusted code, compliant:    " + UntrustedPlugin.peek(new ApiCredentials("sk_live_51Hx9")));
        System.out.println("nested Diagnostics class is private: noncompliant "
                + Modifier.isPrivate(ApiCredentialsNoncompliant.Diagnostics.class.getModifiers())
                + ", compliant " + ApiCredentials.diagnosticsIsPrivate());
    }
}

// Noncompliant: a public inner class exposes the private key
class ApiCredentialsNoncompliant {
    private final String apiKey;

    ApiCredentialsNoncompliant(String apiKey) {
        this.apiKey = apiKey;
    }

    // Meant for support staff, but reachable by anyone who has the outer object
    public class Diagnostics {
        public String dump() {
            return "key=" + apiKey + ", length=" + apiKey.length();
        }
    }
}

// Compliant: the nested class is private and only sees a masked value
class ApiCredentials {
    private final String apiKey;

    ApiCredentials(String apiKey) {
        this.apiKey = apiKey;
    }

    private class Diagnostics {
        String dump() {
            return "key=" + apiKey.substring(0, 8) + "..., length=" + apiKey.length();
        }
    }

    // The outer class decides what leaves its boundary
    public String diagnostics() {
        return new Diagnostics().dump();
    }

    static boolean diagnosticsIsPrivate() {
        return Modifier.isPrivate(Diagnostics.class.getModifiers());
    }
}

// Stands for third-party code that gets hold of the credentials object
class UntrustedPlugin {
    static String peek(Object credentials) {
        if (credentials instanceof ApiCredentialsNoncompliant c) {
            return c.new Diagnostics().dump(); // compiles: the inner class is public
        }
        if (credentials instanceof ApiCredentials c) {
            // c.new Diagnostics() would not compile here: Diagnostics is private
            return c.diagnostics();
        }
        return "?";
    }
}

/* Output:
untrusted code, noncompliant: key=sk_live_51Hx9, length=13
untrusted code, compliant:    key=sk_live_..., length=13
nested Diagnostics class is private: noncompliant false, compliant true
*/
