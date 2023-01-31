// A functional interface has exactly one abstract method, so a lambda or a
// method reference can implement it. @FunctionalInterface is optional, but it
// makes the compiler reject a second abstract method, which would break every
// lambda written against the interface.
//  - default and static methods do not count, so the interface can still
//    offer helpers and combinators
//  - abstract methods that override public Object methods (equals, toString)
//    do not count either
// Write your own when a name says more than Function<A, B>, or when the method
// must throw a checked exception (the java.util.function types cannot).
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class FunctionalInterfaceExample {

    // A domain-specific single-method interface with a default combinator
    @FunctionalInterface
    interface AccessRule {
        boolean allows(String user, String resource);

        default AccessRule and(AccessRule other) {
            return (u, r) -> allows(u, r) && other.allows(u, r);
        }

        static AccessRule denyAll() {
            return (u, r) -> false;
        }

        // Re-declaring an Object method does not add an abstract method
        @Override
        String toString();
    }

    // The java.util.function types cannot throw checked exceptions; this can
    @FunctionalInterface
    interface ConfigLoader {
        String load(String name) throws IOException;
    }

    public static void main(String[] args) {
        Map<String, List<String>> roles = Map.of(
                "alice", List.of("admin", "dev"),
                "bob", List.of("dev"),
                "eve", List.of());

        AccessRule hasAnyRole = (user, resource) -> !roles.getOrDefault(user, List.of()).isEmpty();
        AccessRule adminForSecrets = (user, resource) ->
                !resource.startsWith("/secrets") || roles.getOrDefault(user, List.of()).contains("admin");
        AccessRule policy = hasAnyRole.and(adminForSecrets);

        for (String user : List.of("alice", "bob", "eve")) {
            for (String resource : List.of("/repo", "/secrets/db")) {
                System.out.printf("%-5s %-11s -> %s%n", user, resource,
                        policy.allows(user, resource) ? "allow" : "deny");
            }
        }
        System.out.println("denyAll(alice, /repo) -> " + AccessRule.denyAll().allows("alice", "/repo"));

        ConfigLoader loader = name -> {
            if (name.endsWith(".yml")) return "loaded " + name;
            throw new IOException("unsupported format: " + name);
        };
        for (String name : List.of("app.yml", "app.ini")) {
            try {
                System.out.println(loader.load(name));
            } catch (IOException e) {
                System.out.println("IOException: " + e.getMessage());
            }
        }
    }
}

/* Output:
alice /repo       -> allow
alice /secrets/db -> allow
bob   /repo       -> allow
bob   /secrets/db -> deny
eve   /repo       -> deny
eve   /secrets/db -> deny
denyAll(alice, /repo) -> false
loaded app.yml
IOException: unsupported format: app.ini
*/
