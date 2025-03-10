// Prim's algorithm grows a minimum spanning tree (MST) from one vertex: keep
// the set of vertices already in the tree, and repeatedly add the cheapest
// edge that leaves that set. The cut property makes this safe: for any split
// of the vertices into two sides, a lightest edge crossing it belongs to some
// MST. A priority queue of candidate edges makes "cheapest leaving edge" fast;
// as in Dijkstra, edges whose far end has meanwhile joined the tree are simply
// skipped when polled ("lazy" Prim), giving O(E log E). Kruskal's algorithm
// (see UnionFind.java) instead sorts all edges and adds each one that joins
// two different components; both find a tree of the same total weight. Prim
// suits dense graphs given as adjacency lists; Kruskal suits edge lists. On a
// disconnected graph one Prim run spans only its start's component; starting
// again from every unvisited vertex yields a minimum spanning forest.
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

public class PrimMst {
    record Edge(int from, int to, int weight) { }

    record Forest(List<Edge> edges, long weight, int trees) { }

    static Forest prim(List<List<Edge>> graph) {
        int n = graph.size();
        boolean[] inTree = new boolean[n];
        List<Edge> chosen = new ArrayList<>();
        long total = 0;
        int trees = 0;
        PriorityQueue<Edge> pq = new PriorityQueue<>(
                Comparator.comparingInt(Edge::weight).thenComparingInt(Edge::to)); // stable tie-break
        for (int start = 0; start < n; start++) {
            if (inTree[start]) {
                continue;
            }
            trees++; // a new component: grow another tree from here
            inTree[start] = true;
            pq.addAll(graph.get(start));
            while (!pq.isEmpty()) {
                Edge e = pq.poll();
                if (inTree[e.to()]) {
                    continue; // stale: both ends are already in the tree
                }
                inTree[e.to()] = true;
                chosen.add(e);
                total += e.weight();
                for (Edge next : graph.get(e.to())) {
                    if (!inTree[next.to()]) {
                        pq.add(next);
                    }
                }
            }
        }
        return new Forest(chosen, total, trees);
    }

    // Reference: Kruskal with a tiny union-find
    static long kruskal(int n, List<Edge> edges) {
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        List<Edge> sorted = new ArrayList<>(edges);
        sorted.sort(Comparator.comparingInt(Edge::weight));
        long total = 0;
        for (Edge e : sorted) {
            int a = find(parent, e.from()), b = find(parent, e.to());
            if (a != b) {
                parent[a] = b;
                total += e.weight();
            }
        }
        return total;
    }

    static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    static List<List<Edge>> undirected(int n, List<Edge> edges) {
        List<List<Edge>> g = new ArrayList<>();
        for (int i = 0; i < n; i++) g.add(new ArrayList<>());
        for (Edge e : edges) {
            g.get(e.from()).add(e);
            g.get(e.to()).add(new Edge(e.to(), e.from(), e.weight()));
        }
        return g;
    }

    public static void main(String[] args) {
        // Laying cable between offices; 7 and 8 are a separate site with no link to the rest
        String[] names = {"HQ", "Lab", "Dock", "Mill", "Shop", "Yard", "Barn", "Pier", "Cove"};
        List<Edge> cables = List.of(
                new Edge(0, 1, 4), new Edge(0, 2, 3), new Edge(1, 2, 1), new Edge(1, 3, 2),
                new Edge(2, 3, 4), new Edge(3, 4, 2), new Edge(4, 5, 6), new Edge(3, 5, 7),
                new Edge(2, 6, 9), new Edge(5, 6, 3), new Edge(7, 8, 5));
        Forest f = prim(undirected(names.length, cables));
        for (Edge e : f.edges()) {
            System.out.printf("  %-4s - %-4s %d%n", names[e.from()], names[e.to()], e.weight());
        }
        System.out.println("total " + f.weight() + " over " + f.edges().size() + " edges, "
                + f.trees() + " trees (V - trees = " + (names.length - f.trees()) + ")");
        System.out.println("Kruskal total: " + kruskal(names.length, cables));

        // Self-check against Kruskal on random graphs, connected or not
        Random rnd = new Random(31);
        boolean ok = true;
        for (int t = 0; t < 300; t++) {
            int n = 1 + rnd.nextInt(40);
            List<Edge> edges = new ArrayList<>();
            for (int k = rnd.nextInt(n * 3 + 1); k > 0; k--) {
                edges.add(new Edge(rnd.nextInt(n), rnd.nextInt(n), rnd.nextInt(100)));
            }
            Forest pf = prim(undirected(n, edges));
            ok &= pf.weight() == kruskal(n, edges) && pf.edges().size() == n - pf.trees();
        }
        System.out.println("300 random graphs: Prim weight equals Kruskal, edges = V - trees: " + ok);
    }
}

/* Output:
  HQ   - Dock 3
  Dock - Lab  1
  Lab  - Mill 2
  Mill - Shop 2
  Shop - Yard 6
  Yard - Barn 3
  Pier - Cove 5
total 22 over 7 edges, 2 trees (V - trees = 7)
Kruskal total: 22
300 random graphs: Prim weight equals Kruskal, edges = V - trees: true
*/
