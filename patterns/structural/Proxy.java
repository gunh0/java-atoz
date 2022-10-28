// Proxy (structural): provide a surrogate or placeholder for another object
// to control access to it.
//
// The proxy implements the same interface as the real subject and decides
// whether, when and how calls reach it. Common kinds: a PROTECTION proxy
// checks permissions, a VIRTUAL proxy delays expensive creation, and a
// logging/remote proxy adds plumbing around each call. This file shows a
// hand-written protection + lazy proxy for a secrets vault, the same thing
// generated at runtime with java.lang.reflect.Proxy, and the JDK's own
// protection proxy: Collections.unmodifiableList.
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProxyDemo {
    interface SecretVault {
        String read(String name);
        List<String> list();
    }

    // The real subject: pretend opening it is expensive (unsealing, network)
    static final class RealVault implements SecretVault {
        private final Map<String, String> secrets = Map.of("db-password", "s3cr3t!", "api-key", "ak_9f8e7d");
        RealVault() { System.out.println("  (vault unsealed)"); }
        public String read(String name) { return secrets.get(name); }
        public List<String> list() { return secrets.keySet().stream().sorted().toList(); }
    }

    // A hand-written proxy: protection (role check) + virtual (lazy creation)
    static final class GuardedVault implements SecretVault {
        private final String role;
        private SecretVault real; // created on first permitted use

        GuardedVault(String role) { this.role = role; }

        private SecretVault real() {
            if (real == null) real = new RealVault();
            return real;
        }

        public List<String> list() { return real().list(); } // names are not secret

        public String read(String name) {
            if (!role.equals("admin")) {
                throw new SecurityException(role + " may not read " + name);
            }
            return real().read(name);
        }
    }

    // A dynamic proxy: one InvocationHandler can guard ANY interface
    static <T> T audited(Class<T> type, T target, List<String> auditLog) {
        InvocationHandler handler = (Object proxy, Method method, Object[] args) -> {
            auditLog.add(method.getName() + (args == null ? "()" : Arrays.toString(args)));
            return method.invoke(target, args);
        };
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    public static void main(String[] args) {
        System.out.println("1. protection + virtual proxy");
        SecretVault forDev = new GuardedVault("developer");
        System.out.println("  proxy created, vault not unsealed yet");
        try {
            forDev.read("db-password");
        } catch (SecurityException e) {
            System.out.println("  denied: " + e.getMessage());
        }
        System.out.println("  developer lists names: " + forDev.list());

        SecretVault forAdmin = new GuardedVault("admin");
        System.out.println("  admin reads api-key: " + forAdmin.read("api-key"));

        System.out.println("2. dynamic proxy with java.lang.reflect.Proxy");
        List<String> auditLog = new ArrayList<>();
        SecretVault audited = audited(SecretVault.class, forAdmin, auditLog);
        audited.list();
        audited.read("db-password");
        System.out.println("  audit log: " + auditLog);
        System.out.println("  is a proxy class: " + Proxy.isProxyClass(audited.getClass()));

        System.out.println("3. Collections.unmodifiableList is a protection proxy");
        List<String> allowedHosts = new ArrayList<>(List.of("10.0.0.5", "10.0.0.6"));
        List<String> readOnly = Collections.unmodifiableList(allowedHosts);
        try {
            readOnly.add("0.0.0.0");
        } catch (UnsupportedOperationException e) {
            System.out.println("  add through the proxy: UnsupportedOperationException");
        }
        allowedHosts.add("10.0.0.7"); // the owner still can, and the proxy sees it
        System.out.println("  view after owner change: " + readOnly);
        Set<String> copy = Set.copyOf(allowedHosts); // a copy, unlike a proxy, does not follow changes
        System.out.println("  Set.copyOf size: " + copy.size());
    }
}

/* Output:
1. protection + virtual proxy
  proxy created, vault not unsealed yet
  denied: developer may not read db-password
  (vault unsealed)
  developer lists names: [api-key, db-password]
  (vault unsealed)
  admin reads api-key: ak_9f8e7d
2. dynamic proxy with java.lang.reflect.Proxy
  audit log: [list(), read[db-password]]
  is a proxy class: true
3. Collections.unmodifiableList is a protection proxy
  add through the proxy: UnsupportedOperationException
  view after owner change: [10.0.0.5, 10.0.0.6, 10.0.0.7]
  Set.copyOf size: 3
*/
