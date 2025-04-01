// Floyd-Warshall computes shortest paths between ALL pairs of vertices in
// O(V^3) time and O(V^2) space, with negative edges allowed. It is dynamic
// programming over intermediate vertices: after step k, dist[i][j] is the
// shortest i -> j path whose inner vertices are all among 0..k, because such a
// path either avoids k or goes i -> k -> j. Three nested loops, k outermost,
// and the table can be updated in place. next[i][j] (the first hop on the best
// path) rebuilds any path in O(length). A negative cycle shows up on the
// diagonal: dist[v][v] < 0 means v can reach itself at negative cost, and any
// pair whose path can pass through such a v has no shortest path at all.
// Replacing (min, +) with (or, and) gives Warshall's transitive closure:
// reach[i][j] = "is there any path from i to j". For sparse graphs, V runs of
// Dijkstra (or Johnson's algorithm with negative edges) are faster.
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

public class FloydWarshall {
    static final long INF = Long.MAX_VALUE / 4; // room to add two of them without overflow

    record Result(long[][] dist, int[][] next) {
        boolean hasNegativeCycle() {
            for (int v = 0; v < dist.length; v++) {
                if (dist[v][v] < 0) return true;
            }
            return false;
        }

        List<Integer> path(int from, int to) {
            if (dist[from][to] >= INF) return List.of();
            List<Integer> p = new ArrayList<>(List.of(from));
            for (int v = from; v != to; v = next[v][to]) {
                p.add(next[v][to]);
            }
            return p;
        }
    }

    // edges: {from, to, weight}
    static Result allPairs(int n, int[][] edges) {
        long[][] dist = new long[n][n];
        int[][] next = new int[n][n];
        for (int i = 0; i < n; i++) {
            Arrays.fill(dist[i], INF);
            Arrays.fill(next[i], -1);
            dist[i][i] = 0;
            next[i][i] = i;
        }
        for (int[] e : edges) {
            if (e[2] < dist[e[0]][e[1]]) { // keep the cheapest parallel edge
                dist[e[0]][e[1]] = e[2];
                next[e[0]][e[1]] = e[1];
            }
        }
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                if (dist[i][k] >= INF) continue;
                for (int j = 0; j < n; j++) {
                    if (dist[k][j] < INF && dist[i][k] + dist[k][j] < dist[i][j]) {
                        dist[i][j] = dist[i][k] + dist[k][j];
                        next[i][j] = next[i][k]; // first hop: same as towards k
                    }
                }
            }
        }
        return new Result(dist, next);
    }

    static boolean[][] transitiveClosure(int n, int[][] edges) {
        boolean[][] reach = new boolean[n][n];
        for (int i = 0; i < n; i++) reach[i][i] = true;
        for (int[] e : edges) reach[e[0]][e[1]] = true;
        for (int k = 0; k < n; k++)
            for (int i = 0; i < n; i++)
                if (reach[i][k])
                    for (int j = 0; j < n; j++)
                        reach[i][j] |= reach[k][j];
        return reach;
    }

    static long[] dijkstra(int n, int[][] edges, int s) {
        List<List<int[]>> g = new ArrayList<>();
        for (int i = 0; i < n; i++) g.add(new ArrayList<>());
        for (int[] e : edges) g.get(e[0]).add(e);
        long[] dist = new long[n];
        Arrays.fill(dist, INF);
        dist[s] = 0;
        PriorityQueue<long[]> pq = new PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));
        pq.add(new long[] {0, s});
        while (!pq.isEmpty()) {
            long[] top = pq.poll();
            int u = (int) top[1];
            if (top[0] > dist[u]) continue;
            for (int[] e : g.get(u)) {
                if (dist[u] + e[2] < dist[e[1]]) {
                    dist[e[1]] = dist[u] + e[2];
                    pq.add(new long[] {dist[e[1]], e[1]});
                }
            }
        }
        return dist;
    }

    static String cell(long d) {
        return d >= INF ? "  ." : String.format("%3d", d);
    }

    public static void main(String[] args) {
        String[] names = {"A", "B", "C", "D", "E"};
        int[][] edges = {{0, 1, 3}, {0, 2, 8}, {0, 4, -4}, {1, 3, 1}, {1, 4, 7},
                {2, 1, 4}, {3, 0, 2}, {3, 2, -5}, {4, 3, 6}};
        Result r = allPairs(names.length, edges);
        System.out.println("     A   B   C   D   E");
        for (int i = 0; i < names.length; i++) {
            StringBuilder row = new StringBuilder(names[i] + " ");
            for (int j = 0; j < names.length; j++) row.append(' ').append(cell(r.dist()[i][j]));
            System.out.println(row);
        }
        System.out.println("A -> B: " + r.path(0, 1).stream().map(i -> names[i]).toList()
                + ", C -> E: " + r.path(2, 4).stream().map(i -> names[i]).toList());
        System.out.println("negative cycle: " + r.hasNegativeCycle());

        int[][] withCycle = {{0, 1, 1}, {1, 2, -3}, {2, 1, 1}, {2, 3, 2}};
        Result bad = allPairs(4, withCycle);
        System.out.println("B -> C -> B costs -2: negative cycle " + bad.hasNegativeCycle()
                + ", dist[B][B] = " + bad.dist()[1][1]);

        boolean[][] reach = transitiveClosure(4, new int[][] {{0, 1}, {1, 2}, {3, 0}});
        StringBuilder sb = new StringBuilder("closure of 3->0->1->2: ");
        for (boolean[] row : reach) {
            for (boolean b : row) sb.append(b ? '1' : '0');
            sb.append(' ');
        }
        System.out.println(sb.toString().strip());

        // Self-check: each row equals a Dijkstra run on random non-negative graphs
        Random rnd = new Random(4);
        boolean ok = true;
        for (int t = 0; t < 100; t++) {
            int n = 1 + rnd.nextInt(25);
            int[][] es = new int[rnd.nextInt(n * 4)][];
            for (int k = 0; k < es.length; k++) es[k] = new int[] {rnd.nextInt(n), rnd.nextInt(n), rnd.nextInt(50)};
            Result fw = allPairs(n, es);
            boolean[][] cl = transitiveClosure(n, es);
            for (int s = 0; s < n; s++) {
                long[] d = dijkstra(n, es, s);
                ok &= Arrays.equals(fw.dist()[s], d);
                for (int v = 0; v < n; v++) {
                    ok &= cl[s][v] == (d[v] < INF);
                    List<Integer> p = fw.path(s, v); // path cost must equal the distance
                    long cost = 0;
                    for (int i = 0; i + 1 < p.size(); i++) cost += fw.dist()[p.get(i)][p.get(i + 1)];
                    ok &= d[v] >= INF ? p.isEmpty() : cost == d[v];
                }
            }
        }
        System.out.println("100 random graphs: distances, paths and closure match Dijkstra: " + ok);
    }
}

/* Output:
     A   B   C   D   E
A    0   1  -3   2  -4
B    3   0  -4   1  -1
C    7   4   0   5   3
D    2  -1  -5   0  -2
E    8   5   1   6   0
A -> B: [A, E, D, C, B], C -> E: [C, B, D, A, E]
negative cycle: false
B -> C -> B costs -2: negative cycle true, dist[B][B] = -2
closure of 3->0->1->2: 1110 0110 0010 1111
100 random graphs: distances, paths and closure match Dijkstra: true
*/
