// Maximum flow: how much can be pushed from a source s to a sink t through a
// network whose edges have capacities? Ford-Fulkerson repeatedly finds an
// augmenting path in the RESIDUAL graph (remaining capacity forward, plus the
// flow already sent as capacity backwards, so earlier choices can be undone)
// and pushes its bottleneck along it. Edmonds-Karp picks the SHORTEST
// augmenting path by BFS, which bounds the number of augmentations by O(V E)
// and the running time by O(V E^2), independent of the capacities.
// When no path is left, the vertices still reachable from s form a minimum
// cut: the saturated edges leaving that set have total capacity equal to the
// max flow (max-flow min-cut theorem), i.e. they are the bottleneck.
// Bipartite matching is a special case: source -> every left vertex, every
// right vertex -> sink, all capacities 1; the max flow is the size of a
// maximum matching, and the edges carrying flow are the matched pairs.
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Random;

public class EdmondsKarp {
    static final class Network {
        final int n;
        final List<int[]> edges = new ArrayList<>(); // {from, to, capacity, flow}; edge i^1 is the reverse of i
        final List<List<Integer>> adj = new ArrayList<>();
        int augmentations;

        Network(int n) {
            this.n = n;
            for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
        }

        int addEdge(int from, int to, int capacity) {
            edges.add(new int[] {from, to, capacity, 0});
            adj.get(from).add(edges.size() - 1);
            edges.add(new int[] {to, from, 0, 0}); // residual reverse edge
            adj.get(to).add(edges.size() - 1);
            return edges.size() - 2;
        }

        int residual(int e) {
            return edges.get(e)[2] - edges.get(e)[3];
        }

        long maxFlow(int s, int t) {
            long total = 0;
            while (true) {
                int[] via = new int[n]; // edge used to reach each vertex in the BFS
                Arrays.fill(via, -1);
                Deque<Integer> q = new ArrayDeque<>(List.of(s));
                boolean[] seen = new boolean[n];
                seen[s] = true;
                while (!q.isEmpty() && !seen[t]) {
                    int u = q.poll();
                    for (int e : adj.get(u)) {
                        int v = edges.get(e)[1];
                        if (!seen[v] && residual(e) > 0) {
                            seen[v] = true;
                            via[v] = e;
                            q.add(v);
                        }
                    }
                }
                if (!seen[t]) return total; // no augmenting path: the flow is maximal
                int bottleneck = Integer.MAX_VALUE;
                for (int v = t; v != s; v = edges.get(via[v])[0]) {
                    bottleneck = Math.min(bottleneck, residual(via[v]));
                }
                for (int v = t; v != s; v = edges.get(via[v])[0]) {
                    edges.get(via[v])[3] += bottleneck;
                    edges.get(via[v] ^ 1)[3] -= bottleneck; // allow undoing it later
                }
                total += bottleneck;
                augmentations++;
            }
        }

        // After maxFlow: vertices reachable from s in the residual graph
        boolean[] sourceSide(int s) {
            boolean[] seen = new boolean[n];
            Deque<Integer> q = new ArrayDeque<>(List.of(s));
            seen[s] = true;
            while (!q.isEmpty()) {
                int u = q.poll();
                for (int e : adj.get(u)) {
                    int v = edges.get(e)[1];
                    if (!seen[v] && residual(e) > 0) {
                        seen[v] = true;
                        q.add(v);
                    }
                }
            }
            return seen;
        }
    }

    // Reference matching: Kuhn's augmenting paths, O(V E)
    static int kuhn(int left, int right, List<List<Integer>> likes) {
        int[] matchOfRight = new int[right];
        Arrays.fill(matchOfRight, -1);
        int size = 0;
        for (int u = 0; u < left; u++) {
            if (tryKuhn(u, likes, matchOfRight, new boolean[right])) size++;
        }
        return size;
    }

    static boolean tryKuhn(int u, List<List<Integer>> likes, int[] matchOfRight, boolean[] used) {
        for (int v : likes.get(u)) {
            if (used[v]) continue;
            used[v] = true;
            if (matchOfRight[v] == -1 || tryKuhn(matchOfRight[v], likes, matchOfRight, used)) {
                matchOfRight[v] = u;
                return true;
            }
        }
        return false;
    }

    static int matchingByFlow(int left, int right, List<List<Integer>> likes, List<int[]> pairsOut) {
        int s = left + right, t = s + 1;
        Network net = new Network(left + right + 2);
        List<int[]> middle = new ArrayList<>(); // {edge id, u, v}
        for (int u = 0; u < left; u++) net.addEdge(s, u, 1);
        for (int v = 0; v < right; v++) net.addEdge(left + v, t, 1);
        for (int u = 0; u < left; u++)
            for (int v : likes.get(u)) middle.add(new int[] {net.addEdge(u, left + v, 1), u, v});
        int size = (int) net.maxFlow(s, t);
        if (pairsOut != null) {
            for (int[] m : middle) if (net.edges.get(m[0])[3] == 1) pairsOut.add(new int[] {m[1], m[2]});
        }
        return size;
    }

    public static void main(String[] args) {
        // Pipe network: s=0, t=5 (the classic CLRS example)
        String[] names = {"s", "v1", "v2", "v3", "v4", "t"};
        int[][] pipes = {{0, 1, 16}, {0, 2, 13}, {1, 3, 12}, {2, 1, 4}, {2, 4, 14},
                {3, 2, 9}, {3, 5, 20}, {4, 3, 7}, {4, 5, 4}};
        Network net = new Network(names.length);
        for (int[] p : pipes) net.addEdge(p[0], p[1], p[2]);
        System.out.println("max flow s -> t: " + net.maxFlow(0, 5) + " in " + net.augmentations + " augmentations");
        boolean[] side = net.sourceSide(0);
        int cut = 0;
        List<String> cutEdges = new ArrayList<>();
        for (int[] p : pipes) {
            if (side[p[0]] && !side[p[1]]) {
                cut += p[2];
                cutEdges.add(names[p[0]] + "->" + names[p[1]] + "(" + p[2] + ")");
            }
        }
        List<String> sSide = new ArrayList<>();
        for (int v = 0; v < names.length; v++) if (side[v]) sSide.add(names[v]);
        System.out.println("min cut " + sSide + " | rest, edges " + cutEdges + ", capacity " + cut);

        // Assigning reviewers to pull requests: reviewer i may review the PRs in likes[i]
        String[] reviewers = {"ann", "bob", "cat", "dan"};
        String[] prs = {"#101", "#102", "#103", "#104"};
        List<List<Integer>> likes = List.of(List.of(0, 1), List.of(0), List.of(1, 2, 3), List.of(0, 2));
        List<int[]> pairs = new ArrayList<>();
        int size = matchingByFlow(reviewers.length, prs.length, likes, pairs);
        List<String> shown = pairs.stream().map(p -> reviewers[p[0]] + "=" + prs[p[1]]).toList();
        System.out.println("matching of size " + size + ": " + shown);

        // Self-check: flow matching size equals Kuhn's algorithm; flow value equals min-cut capacity
        Random rnd = new Random(8);
        boolean ok = true;
        for (int trial = 0; trial < 200; trial++) {
            int l = 1 + rnd.nextInt(20), r = 1 + rnd.nextInt(20);
            List<List<Integer>> g = new ArrayList<>();
            for (int u = 0; u < l; u++) {
                List<Integer> row = new ArrayList<>();
                for (int v = 0; v < r; v++) if (rnd.nextInt(100) < 15) row.add(v);
                g.add(row);
            }
            ok &= matchingByFlow(l, r, g, null) == kuhn(l, r, g);

            int n = 2 + rnd.nextInt(15);
            Network rn = new Network(n);
            List<int[]> es = new ArrayList<>();
            for (int k = rnd.nextInt(n * 4); k > 0; k--) {
                int[] e = {rnd.nextInt(n), rnd.nextInt(n), rnd.nextInt(30)};
                es.add(e);
                rn.addEdge(e[0], e[1], e[2]);
            }
            long flow = rn.maxFlow(0, n - 1);
            boolean[] sd = rn.sourceSide(0);
            long capacity = es.stream().filter(e -> sd[e[0]] && !sd[e[1]]).mapToLong(e -> e[2]).sum();
            ok &= flow == capacity && !sd[n - 1];
        }
        System.out.println("200 random cases: matching = Kuhn, max flow = min cut: " + ok);
    }
}

/* Output:
max flow s -> t: 23 in 3 augmentations
min cut [s, v1, v2, v4] | rest, edges [v1->v3(12), v4->v3(7), v4->t(4)], capacity 23
matching of size 4: [ann=#102, bob=#101, cat=#104, dan=#103]
200 random cases: matching = Kuhn, max flow = min cut: true
*/
