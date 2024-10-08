// Topological sort orders the vertices of a directed acyclic graph so that every
// edge u -> v has u before v: build steps, course prerequisites, module
// dependencies. Kahn's algorithm counts incoming edges (in-degree), starts from
// all vertices with none, and each time it emits a vertex it removes its outgoing
// edges, releasing vertices whose in-degree drops to zero. If fewer than V
// vertices come out, the rest sit on a cycle (or behind one) and no order exists.
// O(V + E). A PriorityQueue instead of a FIFO picks the smallest ready vertex each
// time, giving the lexicographically smallest order and a stable output.
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class TopologicalSort {
    // Kahn's algorithm; returns the vertices it could emit (all of them iff acyclic)
    static <T extends Comparable<T>> List<T> emit(Map<T, List<T>> edges, Map<T, Integer> inDegree) {
        edges.forEach((u, targets) -> {
            inDegree.putIfAbsent(u, 0);
            for (T v : targets) {
                inDegree.merge(v, 1, Integer::sum);
            }
        });
        PriorityQueue<T> ready = new PriorityQueue<>();
        inDegree.forEach((v, d) -> {
            if (d == 0) {
                ready.add(v);
            }
        });
        List<T> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            T u = ready.poll();
            order.add(u);
            for (T v : edges.getOrDefault(u, List.of())) {
                if (inDegree.merge(v, -1, Integer::sum) == 0) {
                    ready.add(v);
                }
            }
        }
        return order;
    }

    static <T extends Comparable<T>> Optional<List<T>> sort(Map<T, List<T>> edges) {
        Map<T, Integer> inDegree = new HashMap<>();
        List<T> order = emit(edges, inDegree);
        return order.size() == inDegree.size() ? Optional.of(order) : Optional.empty();
    }

    // Vertices Kahn never released: on a cycle or downstream of one
    static <T extends Comparable<T>> Set<T> blocked(Map<T, List<T>> edges) {
        Map<T, Integer> inDegree = new HashMap<>();
        List<T> order = emit(edges, inDegree);
        Set<T> rest = new TreeSet<>(inDegree.keySet());
        order.forEach(rest::remove);
        return rest;
    }

    static <T> boolean respectsEdges(List<T> order, Map<T, List<T>> edges) {
        Map<T, Integer> pos = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            pos.put(order.get(i), i);
        }
        for (Map.Entry<T, List<T>> e : edges.entrySet()) {
            for (T v : e.getValue()) {
                if (pos.get(e.getKey()) >= pos.get(v)) {
                    return false;
                }
            }
        }
        return true;
    }

    static boolean pathExists(Map<Integer, List<Integer>> g, int from, int to) {
        Set<Integer> seen = new HashSet<>();
        List<Integer> stack = new ArrayList<>(List.of(from));
        while (!stack.isEmpty()) {
            int u = stack.remove(stack.size() - 1);
            if (u == to) {
                return true;
            }
            if (seen.add(u)) {
                stack.addAll(g.getOrDefault(u, List.of()));
            }
        }
        return false;
    }

    public static void main(String[] args) {
        // prerequisite -> courses that need it
        Map<String, List<String>> courses = new TreeMap<>();
        courses.put("intro-programming", List.of("data-structures", "discrete-math"));
        courses.put("discrete-math", List.of("algorithms", "cryptography"));
        courses.put("data-structures", List.of("algorithms", "databases"));
        courses.put("algorithms", List.of("compilers"));
        courses.put("networks", List.of("network-security"));
        courses.put("cryptography", List.of("network-security"));
        List<String> plan = sort(courses).orElseThrow();
        for (int i = 0; i < plan.size(); i++) {
            System.out.println((i + 1) + ". " + plan.get(i));
        }
        System.out.println("respects all prerequisites: " + respectsEdges(plan, courses));

        Map<String, List<String>> cyclic = new TreeMap<>(courses);
        // data-structures -> algorithms -> compilers -> data-structures
        cyclic.put("compilers", List.of("data-structures"));
        System.out.println("with a cycle: " + sort(cyclic).map(List::toString).orElse("no order exists"));
        System.out.println("blocked: " + blocked(cyclic));

        // Self-check on random DAGs: edges only go from lower to higher position,
        // vertex ids are shuffled labels. Adding last -> first closes a cycle
        // exactly when first already reaches last.
        Random rnd = new Random(19);
        boolean ok = true;
        for (int t = 0; t < 200; t++) {
            int n = 1 + rnd.nextInt(20);
            int[] label = rnd.ints(0, 1000).distinct().limit(n).toArray();
            Map<Integer, List<Integer>> dag = new HashMap<>();
            for (int u = 0; u < n; u++) {
                List<Integer> out = new ArrayList<>();
                for (int v = u + 1; v < n; v++) {
                    if (rnd.nextInt(4) == 0) {
                        out.add(label[v]);
                    }
                }
                dag.put(label[u], out);
            }
            Optional<List<Integer>> order = sort(dag);
            ok &= order.isPresent() && order.get().size() == n && respectsEdges(order.get(), dag);
            if (n > 1) {
                boolean firstReachesLast = pathExists(dag, label[0], label[n - 1]);
                dag.get(label[n - 1]).add(label[0]);
                ok &= sort(dag).isEmpty() == firstReachesLast;
            }
        }
        System.out.println("200 random DAGs ordered correctly, cycles detected: " + ok);
    }
}

/* Output:
1. intro-programming
2. data-structures
3. databases
4. discrete-math
5. algorithms
6. compilers
7. cryptography
8. networks
9. network-security
respects all prerequisites: true
with a cycle: no order exists
blocked: [algorithms, compilers, data-structures, databases]
200 random DAGs ordered correctly, cycles detected: true
*/
