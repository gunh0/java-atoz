// Strategy (behavioral): define a family of algorithms, encapsulate each one,
// and make them interchangeable. The strategy lets the algorithm vary
// independently from the clients that use it.
//
// Here a load balancer (the context) delegates "which backend gets the next
// request?" to a pluggable Strategy: round robin, least connections, or
// sticky source-IP hashing. The balancer itself has no if/else over policy
// names; swapping the policy is a constructor argument. Because the
// interface has a single method, a lambda is a perfectly good strategy too.
// JDK examples: Comparator passed to sort, RejectedExecutionHandler of a
// ThreadPoolExecutor, and the FilenameFilter/FileFilter callbacks.
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StrategyDemo {
    static final class Backend {
        final String name;
        int active; // open connections

        Backend(String name, int active) {
            this.name = name;
            this.active = active;
        }
    }

    // The strategy interface: one algorithm, many implementations
    @FunctionalInterface
    interface BalancingStrategy {
        Backend pick(List<Backend> backends, String clientIp);
    }

    static final class RoundRobin implements BalancingStrategy {
        private int next;

        public Backend pick(List<Backend> backends, String clientIp) {
            return backends.get(next++ % backends.size());
        }
    }

    static final class LeastConnections implements BalancingStrategy {
        public Backend pick(List<Backend> backends, String clientIp) {
            Backend best = backends.get(0);
            for (Backend b : backends) {
                if (b.active < best.active) best = b;
            }
            return best;
        }
    }

    // Same client always lands on the same backend (session affinity)
    static final class SourceIpHash implements BalancingStrategy {
        public Backend pick(List<Backend> backends, String clientIp) {
            return backends.get(Math.floorMod(clientIp.hashCode(), backends.size()));
        }
    }

    // The context: owns the backends, delegates the decision
    static final class LoadBalancer {
        private final List<Backend> backends;
        private final BalancingStrategy strategy;

        LoadBalancer(List<Backend> backends, BalancingStrategy strategy) {
            this.backends = backends;
            this.strategy = strategy;
        }

        String route(String clientIp) {
            Backend b = strategy.pick(backends, clientIp);
            b.active++;
            return b.name;
        }
    }

    static List<Backend> pool() {
        return new ArrayList<>(List.of(new Backend("app-1", 3), new Backend("app-2", 0), new Backend("app-3", 1)));
    }

    public static void main(String[] args) {
        List<String> clients = List.of("10.0.0.5", "10.0.0.9", "10.0.0.5", "192.168.1.20", "10.0.0.9", "10.0.0.5");

        Map<String, BalancingStrategy> strategies = new LinkedHashMap<>();
        strategies.put("round robin", new RoundRobin());
        strategies.put("least connections", new LeastConnections());
        strategies.put("source ip hash", new SourceIpHash());
        // An ad-hoc strategy as a lambda: drain app-1 for maintenance
        strategies.put("drain app-1 (lambda)", (backends, ip) -> backends.stream()
                .filter(b -> !b.name.equals("app-1"))
                .min((a, b) -> Integer.compare(a.active, b.active))
                .orElseThrow());

        strategies.forEach((name, strategy) -> {
            LoadBalancer lb = new LoadBalancer(pool(), strategy);
            List<String> routed = new ArrayList<>();
            for (String ip : clients) routed.add(lb.route(ip));
            System.out.printf("%-21s %s%n", name + ":", routed);
        });
    }
}

/* Output:
round robin:          [app-1, app-2, app-3, app-1, app-2, app-3]
least connections:    [app-2, app-2, app-3, app-2, app-3, app-1]
source ip hash:       [app-1, app-2, app-1, app-3, app-2, app-1]
drain app-1 (lambda): [app-2, app-2, app-3, app-2, app-3, app-2]
*/
