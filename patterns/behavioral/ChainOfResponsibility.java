// Chain of Responsibility (behavioral): avoid coupling the sender of a
// request to its receiver by giving more than one object a chance to handle
// it. The handlers are linked into a chain and the request travels along it
// until one of them deals with it.
//
// Here an API gateway runs every request through a chain of checks: IP
// blocklist, rate limit, authentication, authorization. Any link may reject
// the request (and stop the chain) or pass it on; the last link serves it.
// Links can be reordered, added or removed without touching the others.
// JDK examples: ClassLoader delegation (each loader asks its parent first)
// and java.util.logging, where a Logger passes records up to its parents.
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChainOfResponsibilityDemo {
    record Request(String ip, String token, String path) {}

    abstract static class Handler {
        private Handler next;

        // returns the next link so chains can be built fluently
        Handler then(Handler next) {
            this.next = next;
            return next;
        }

        final String handle(Request r) {
            String verdict = check(r);
            if (verdict != null) return verdict;         // handled here, chain stops
            if (next == null) return "500 no handler";   // fell off the end
            return next.handle(r);
        }

        // null means "not mine, pass it on"
        protected abstract String check(Request r);
    }

    static final class IpBlocklist extends Handler {
        private final Set<String> blocked;
        IpBlocklist(Set<String> blocked) { this.blocked = blocked; }
        protected String check(Request r) { return blocked.contains(r.ip()) ? "403 blocked ip" : null; }
    }

    static final class RateLimiter extends Handler {
        private final int limit;
        private final Map<String, Integer> counts = new HashMap<>();
        RateLimiter(int limit) { this.limit = limit; }
        protected String check(Request r) {
            int n = counts.merge(r.ip(), 1, Integer::sum);
            return n > limit ? "429 too many requests" : null;
        }
    }

    static final class Authenticator extends Handler {
        private final Map<String, String> tokens;
        Authenticator(Map<String, String> tokens) { this.tokens = tokens; }
        protected String check(Request r) { return tokens.containsKey(r.token()) ? null : "401 unauthenticated"; }
        String userOf(Request r) { return tokens.get(r.token()); }
    }

    static final class Authorizer extends Handler {
        private final Authenticator auth;
        Authorizer(Authenticator auth) { this.auth = auth; }
        protected String check(Request r) {
            boolean adminPath = r.path().startsWith("/admin");
            return adminPath && !auth.userOf(r).equals("admin") ? "403 forbidden" : null;
        }
    }

    static final class Endpoint extends Handler {
        protected String check(Request r) { return "200 ok " + r.path(); }
    }

    public static void main(String[] args) {
        Authenticator auth = new Authenticator(Map.of("t-alice", "alice", "t-admin", "admin"));
        Handler chain = new IpBlocklist(Set.of("198.51.100.66"));
        chain.then(new RateLimiter(3))
             .then(auth)
             .then(new Authorizer(auth))
             .then(new Endpoint());

        List<Request> requests = List.of(
                new Request("198.51.100.66", "t-admin", "/admin/users"),
                new Request("10.0.0.1", "bogus", "/reports"),
                new Request("10.0.0.1", "t-alice", "/reports"),
                new Request("10.0.0.1", "t-alice", "/admin/users"),
                new Request("10.0.0.1", "t-alice", "/reports"),
                new Request("10.0.0.2", "t-admin", "/admin/users"));

        for (Request r : requests) {
            System.out.printf("%-14s %-8s %-13s -> %s%n", r.ip(), r.token(), r.path(), chain.handle(r));
        }
    }
}

/* Output:
198.51.100.66  t-admin  /admin/users  -> 403 blocked ip
10.0.0.1       bogus    /reports      -> 401 unauthenticated
10.0.0.1       t-alice  /reports      -> 200 ok /reports
10.0.0.1       t-alice  /admin/users  -> 403 forbidden
10.0.0.1       t-alice  /reports      -> 429 too many requests
10.0.0.2       t-admin  /admin/users  -> 200 ok /admin/users
*/
