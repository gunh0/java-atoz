// Bellman-Ford computes single-source shortest paths when edge weights may be
// negative. It relaxes every edge, V-1 times: after round i, every vertex
// whose shortest path uses at most i edges has its final distance, and a
// shortest path without cycles has at most V-1 edges. O(V * E), slower than
// Dijkstra's O(E log V), but correct where Dijkstra's "a settled vertex is
// final" argument fails. If a V-th round still improves some distance, a
// negative cycle is reachable and "shortest" is undefined (walk the loop
// forever). To report the cycle: take a vertex relaxed in round V, follow the
// predecessor links V times to be sure to land ON the cycle, then walk them
// until it repeats. Typical use: currency arbitrage (weights -log(rate)),
// routing protocols (distance vector), and difference constraints. Stopping
// early when a round changes nothing makes it fast on most inputs.
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

public class BellmanFord {
    record Edge(int from, int to, long weight) { }

    sealed interface Result permits Distances, NegativeCycle { }
    record Distances(long[] dist, int[] prev, int rounds) implements Result { }
    record NegativeCycle(List<Integer> cycle) implements Result { }

    static final long INF = Long.MAX_VALUE;

    static Result shortestPaths(int n, List<Edge> edges, int source) {
        long[] dist = new long[n];
        int[] prev = new int[n];
        Arrays.fill(dist, INF);
        Arrays.fill(prev, -1);
        dist[source] = 0;
        int rounds = 0;
        int lastRelaxed = -1;
        for (int round = 1; round <= n; round++) { // round n only detects cycles
            lastRelaxed = -1;
            for (Edge e : edges) {
                if (dist[e.from()] != INF && dist[e.from()] + e.weight() < dist[e.to()]) {
                    dist[e.to()] = dist[e.from()] + e.weight();
                    prev[e.to()] = e.from();
                    lastRelaxed = e.to();
                }
            }
            if (lastRelaxed == -1) {
                return new Distances(dist, prev, rounds); // nothing changed: done early
            }
            rounds = round;
        }
        // Still relaxing in round n: walk back n steps to land on the cycle, then collect it
        int v = lastRelaxed;
        for (int i = 0; i < n; i++) {
            v = prev[v];
        }
        List<Integer> cycle = new ArrayList<>();
        int u = v;
        do {
            cycle.add(u);
            u = prev[u];
        } while (u != v);
        cycle.add(v);
        Collections.reverse(cycle);
        return new NegativeCycle(cycle);
    }

    static List<Integer> path(int[] prev, int target) {
        List<Integer> p = new ArrayList<>();
        for (int v = target; v != -1; v = prev[v]) {
            p.add(v);
        }
        Collections.reverse(p);
        return p;
    }

    // Reference for non-negative weights
    static long[] dijkstra(int n, List<Edge> edges, int source) {
        List<List<Edge>> g = new ArrayList<>();
        for (int i = 0; i < n; i++) g.add(new ArrayList<>());
        edges.forEach(e -> g.get(e.from()).add(e));
        long[] dist = new long[n];
        Arrays.fill(dist, INF);
        dist[source] = 0;
        PriorityQueue<long[]> pq = new PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));
        pq.add(new long[] {0, source});
        while (!pq.isEmpty()) {
            long[] top = pq.poll();
            int u = (int) top[1];
            if (top[0] > dist[u]) continue;
            for (Edge e : g.get(u)) {
                if (dist[u] + e.weight() < dist[e.to()]) {
                    dist[e.to()] = dist[u] + e.weight();
                    pq.add(new long[] {dist[e.to()], e.to()});
                }
            }
        }
        return dist;
    }

    public static void main(String[] args) {
        String[] names = {"S", "A", "B", "C", "D"};
        List<Edge> edges = List.of(
                new Edge(0, 1, 6), new Edge(0, 3, 7), new Edge(1, 2, 5), new Edge(1, 3, 8),
                new Edge(1, 4, -4), new Edge(2, 1, -2), new Edge(3, 2, -3), new Edge(3, 4, 9),
                new Edge(4, 0, 2), new Edge(4, 2, 7));
        if (shortestPaths(names.length, edges, 0) instanceof Distances(long[] dist, int[] prev, int rounds)) {
            for (int v = 0; v < names.length; v++) {
                System.out.printf("S -> %s: %3d via %s%n", names[v], dist[v],
                        path(prev, v).stream().map(i -> names[i]).toList());
            }
            System.out.println("distances settled after " + rounds + " rounds (V-1 = " + (names.length - 1) + " is the worst case)");
        }

        // Exchange rates USD, EUR, GBP, JPY with an arbitrage loop; weight = -log(rate) in micro-units
        String[] cur = {"USD", "EUR", "GBP", "JPY"};
        double[][] rate = {
                {1, 0.92, 0.79, 151.0},
                {1.09, 1, 0.86, 164.0},
                {1.27, 1.17, 1, 191.5},
                {0.0066, 0.0061, 0.0052, 1}};
        List<Edge> fx = new ArrayList<>();
        for (int i = 0; i < cur.length; i++) {
            for (int j = 0; j < cur.length; j++) {
                if (i != j) {
                    fx.add(new Edge(i, j, Math.round(-Math.log(rate[i][j]) * 1_000_000)));
                }
            }
        }
        switch (shortestPaths(cur.length, fx, 0)) {
            case NegativeCycle(List<Integer> cycle) -> {
                double gain = 1;
                for (int i = 0; i + 1 < cycle.size(); i++) {
                    gain *= rate[cycle.get(i)][cycle.get(i + 1)];
                }
                System.out.printf("arbitrage cycle %s multiplies money by %.4f%n",
                        cycle.stream().map(i -> cur[i]).toList(), gain);
            }
            case Distances d -> System.out.println("no arbitrage");
        }

        // Self-check: equals Dijkstra on random non-negative graphs
        Random rnd = new Random(9);
        boolean ok = true;
        for (int t = 0; t < 300; t++) {
            int n = 1 + rnd.nextInt(30);
            List<Edge> g = new ArrayList<>();
            for (int k = rnd.nextInt(n * 4); k > 0; k--) {
                g.add(new Edge(rnd.nextInt(n), rnd.nextInt(n), rnd.nextInt(100)));
            }
            int s = rnd.nextInt(n);
            ok &= shortestPaths(n, g, s) instanceof Distances d && Arrays.equals(d.dist(), dijkstra(n, g, s));
        }
        System.out.println("300 random non-negative graphs match Dijkstra: " + ok);

        // Every reported cycle really has negative total weight
        int found = 0;
        boolean cyclesNegative = true;
        for (int t = 0; t < 300; t++) {
            int n = 2 + rnd.nextInt(15);
            List<Edge> g = new ArrayList<>();
            for (int k = n * 2; k > 0; k--) {
                g.add(new Edge(rnd.nextInt(n), rnd.nextInt(n), rnd.nextInt(40) - 8));
            }
            if (shortestPaths(n, g, 0) instanceof NegativeCycle(List<Integer> c)) {
                found++;
                long w = 0;
                for (int i = 0; i + 1 < c.size(); i++) {
                    int a = c.get(i), b = c.get(i + 1);
                    w += g.stream().filter(e -> e.from() == a && e.to() == b)
                            .mapToLong(Edge::weight).min().orElse(INF / 4);
                }
                cyclesNegative &= w < 0;
            }
        }
        System.out.println(found + " negative cycles reported in 300 graphs with negative edges, all negative: "
                + cyclesNegative);
    }
}

/* Output:
S -> S:   0 via [S]
S -> A:   2 via [S, C, B, A]
S -> B:   4 via [S, C, B]
S -> C:   7 via [S, C]
S -> D:  -2 via [S, C, B, A, D]
distances settled after 3 rounds (V-1 = 4 is the worst case)
arbitrage cycle [EUR, GBP, EUR] multiplies money by 1.0062
300 random non-negative graphs match Dijkstra: true
112 negative cycles reported in 300 graphs with negative edges, all negative: true
*/
