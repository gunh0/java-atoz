// Dijkstra's algorithm: single-source shortest paths in a graph with
// non-negative edge weights. A priority queue hands out the closest unsettled
// vertex; once polled, its distance is final, and its edges are relaxed. Java's
// PriorityQueue has no decrease-key, so we push a new (vertex, distance) entry on
// every improvement and skip stale entries when they surface ("lazy deletion").
// O((V + E) log V). A prev[] array rebuilds the actual path. With a negative edge
// the "settled means final" argument breaks, so use Bellman-Ford there.
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

public class Dijkstra {
    record Edge(int to, int weight) { }

    record Result(long[] dist, int[] prev) {
        List<Integer> pathTo(int target) {
            if (dist[target] == Long.MAX_VALUE) {
                return List.of();
            }
            List<Integer> path = new ArrayList<>();
            for (int v = target; v != -1; v = prev[v]) {
                path.add(v);
            }
            Collections.reverse(path);
            return path;
        }
    }

    static Result shortestPaths(List<List<Edge>> graph, int source) {
        int n = graph.size();
        long[] dist = new long[n];
        int[] prev = new int[n];
        Arrays.fill(dist, Long.MAX_VALUE);
        Arrays.fill(prev, -1);
        dist[source] = 0;
        // entries are {distance, vertex}
        PriorityQueue<long[]> pq = new PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));
        pq.add(new long[] {0, source});
        while (!pq.isEmpty()) {
            long[] top = pq.poll();
            int u = (int) top[1];
            if (top[0] > dist[u]) {
                continue; // stale entry: u was already settled with a shorter distance
            }
            for (Edge e : graph.get(u)) {
                long candidate = dist[u] + e.weight();
                if (candidate < dist[e.to()]) {
                    dist[e.to()] = candidate;
                    prev[e.to()] = u;
                    pq.add(new long[] {candidate, e.to()});
                }
            }
        }
        return new Result(dist, prev);
    }

    // Reference: Bellman-Ford, V-1 rounds of relaxing every edge, O(V * E)
    static long[] bellmanFord(List<List<Edge>> graph, int source) {
        int n = graph.size();
        long[] dist = new long[n];
        Arrays.fill(dist, Long.MAX_VALUE);
        dist[source] = 0;
        for (int round = 1; round < n; round++) {
            for (int u = 0; u < n; u++) {
                if (dist[u] == Long.MAX_VALUE) {
                    continue;
                }
                for (Edge e : graph.get(u)) {
                    dist[e.to()] = Math.min(dist[e.to()], dist[u] + e.weight());
                }
            }
        }
        return dist;
    }

    static List<List<Edge>> newGraph(int n) {
        List<List<Edge>> g = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            g.add(new ArrayList<>());
        }
        return g;
    }

    static String show(long d) {
        return d == Long.MAX_VALUE ? "inf" : Long.toString(d);
    }

    public static void main(String[] args) {
        String[] names = {"A", "B", "C", "D", "E", "F", "G"};
        List<List<Edge>> g = newGraph(names.length);
        int[][] edges = {
                {0, 1, 7}, {0, 2, 9}, {0, 5, 14}, {1, 2, 10}, {1, 3, 15},
                {2, 3, 11}, {2, 5, 2}, {3, 4, 6}, {5, 4, 9}};
        for (int[] e : edges) { // undirected road map
            g.get(e[0]).add(new Edge(e[1], e[2]));
            g.get(e[1]).add(new Edge(e[0], e[2]));
        }
        Result r = shortestPaths(g, 0);
        for (int v = 0; v < names.length; v++) {
            List<String> path = r.pathTo(v).stream().map(i -> names[i]).toList();
            System.out.printf("A -> %s: %-4s %s%n", names[v], show(r.dist()[v]), path);
        }

        // Self-check against Bellman-Ford on random directed graphs
        Random rnd = new Random(18);
        boolean ok = true;
        for (int t = 0; t < 200; t++) {
            int n = 1 + rnd.nextInt(25);
            List<List<Edge>> rg = newGraph(n);
            for (int k = rnd.nextInt(n * 4); k > 0; k--) {
                rg.get(rnd.nextInt(n)).add(new Edge(rnd.nextInt(n), rnd.nextInt(50)));
            }
            int s = rnd.nextInt(n);
            ok &= Arrays.equals(shortestPaths(rg, s).dist(), bellmanFord(rg, s));
        }
        System.out.println("200 random graphs match Bellman-Ford: " + ok);
    }
}

/* Output:
A -> A: 0    [A]
A -> B: 7    [A, B]
A -> C: 9    [A, C]
A -> D: 20   [A, C, D]
A -> E: 20   [A, C, F, E]
A -> F: 11   [A, C, F]
A -> G: inf  []
200 random graphs match Bellman-Ford: true
*/
