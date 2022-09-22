// OBJ13-J. Ensure that references to mutable objects are not exposed
// https://wiki.sei.cmu.edu/confluence/display/java/OBJ13-J.+Ensure+that+references+to+mutable+objects+are+not+exposed
//
// "final" freezes a reference, not the object behind it. A public static
// final HashMap of trusted hosts, or a public final array, can be edited by
// any code that sees it. And an unmodifiable WRAPPER is only shallow:
// Collections.unmodifiableList over mutable elements still lets callers
// change each element, and the view reflects later changes to the list it
// wraps. Expose immutable data all the way down: Map.copyOf / List.copyOf
// (which are independent snapshots), and immutable element types such as
// records with immutable components.
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OBJ13_ExposedMutableReferences {
    public static void main(String[] args) {
        // Noncompliant 1: public static final, but a mutable map
        ConfigNoncompliant.TRUSTED_HOSTS.put("evil.example", "trusted");
        System.out.println("noncompliant: evil.example trusted = " + ConfigNoncompliant.isTrusted("evil.example"));

        // Noncompliant 2: unmodifiable view over mutable elements
        List<MutableEndpoint> endpoints = ConfigNoncompliant.endpoints();
        try {
            endpoints.add(new MutableEndpoint("evil.example", 80));
        } catch (UnsupportedOperationException e) {
            System.out.println("noncompliant: the list itself is read-only...");
        }
        endpoints.get(0).host = "evil.example"; // ...but its elements are not
        System.out.println("noncompliant: first endpoint now " + ConfigNoncompliant.endpoints().get(0));

        // Compliant: immutable all the way down
        try {
            ConfigCompliant.TRUSTED_HOSTS.put("evil.example", "trusted");
        } catch (UnsupportedOperationException e) {
            System.out.println("compliant: TRUSTED_HOSTS is immutable");
        }
        System.out.println("compliant: evil.example trusted = " + ConfigCompliant.isTrusted("evil.example"));
        System.out.println("compliant: first endpoint " + ConfigCompliant.ENDPOINTS.get(0)
                + " (records have no setters)");
    }
}

class MutableEndpoint {
    String host;
    int port;

    MutableEndpoint(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }
}

class ConfigNoncompliant {
    public static final Map<String, String> TRUSTED_HOSTS = new HashMap<>(Map.of("api.example", "trusted"));

    private static final List<MutableEndpoint> ENDPOINTS =
            new ArrayList<>(List.of(new MutableEndpoint("api.example", 443)));

    static boolean isTrusted(String host) {
        return "trusted".equals(TRUSTED_HOSTS.get(host));
    }

    static List<MutableEndpoint> endpoints() {
        return Collections.unmodifiableList(ENDPOINTS); // shallow protection only
    }
}

record Endpoint(String host, int port) {
}

final class ConfigCompliant {
    public static final Map<String, String> TRUSTED_HOSTS = Map.copyOf(Map.of("api.example", "trusted"));
    public static final List<Endpoint> ENDPOINTS = List.of(new Endpoint("api.example", 443));

    private ConfigCompliant() {
    }

    static boolean isTrusted(String host) {
        return "trusted".equals(TRUSTED_HOSTS.get(host));
    }
}

/* Output:
noncompliant: evil.example trusted = true
noncompliant: the list itself is read-only...
noncompliant: first endpoint now evil.example:443
compliant: TRUSTED_HOSTS is immutable
compliant: evil.example trusted = false
compliant: first endpoint Endpoint[host=api.example, port=443] (records have no setters)
*/
