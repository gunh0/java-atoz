// Union-find (disjoint set union) keeps a partition of 0..n-1 as a forest: each
// set is a tree, its root is the representative. find() follows parents to the
// root and compresses the path on the way back; union() hangs the shallower tree
// (by rank) under the deeper one. Together they make each operation run in
// amortized O(alpha(n)), effectively constant. Typical uses: connected
// components, "are these two in the same network?", and Kruskal's minimum
// spanning tree (add the cheapest edge that does not close a cycle).
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class UnionFind {
    private final int[] parent;
    private final int[] rank;
    private int components;

    public UnionFind(int n) {
        parent = new int[n];
        rank = new int[n];
        components = n;
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
    }

    public int find(int x) {
        if (parent[x] != x) {
            parent[x] = find(parent[x]); // path compression
        }
        return parent[x];
    }

    // Returns false when a and b were already connected
    public boolean union(int a, int b) {
        int ra = find(a), rb = find(b);
        if (ra == rb) {
            return false;
        }
        if (rank[ra] < rank[rb]) {
            int t = ra;
            ra = rb;
            rb = t;
        }
        parent[rb] = ra;
        if (rank[ra] == rank[rb]) {
            rank[ra]++;
        }
        components--;
        return true;
    }

    public boolean connected(int a, int b) {
        return find(a) == find(b);
    }

    public int components() {
        return components;
    }

    record Edge(int u, int v, int weight) { }

    static List<Edge> kruskal(int n, List<Edge> edges) {
        List<Edge> sorted = new ArrayList<>(edges);
        sorted.sort(Comparator.comparingInt(Edge::weight));
        UnionFind uf = new UnionFind(n);
        List<Edge> tree = new ArrayList<>();
        for (Edge e : sorted) {
            if (uf.union(e.u(), e.v())) {
                tree.add(e);
            }
        }
        return tree;
    }

    // Brute-force reference: label components with a flood fill over the edge list
    static int[] floodLabels(int n, List<int[]> pairs) {
        int[] label = new int[n];
        Arrays.fill(label, -1);
        int next = 0;
        for (int s = 0; s < n; s++) {
            if (label[s] != -1) {
                continue;
            }
            label[s] = next;
            boolean changed = true;
            while (changed) {
                changed = false;
                for (int[] p : pairs) {
                    if (label[p[0]] == next && label[p[1]] == -1) {
                        label[p[1]] = next;
                        changed = true;
                    } else if (label[p[1]] == next && label[p[0]] == -1) {
                        label[p[0]] = next;
                        changed = true;
                    }
                }
            }
            next++;
        }
        return label;
    }

    public static void main(String[] args) {
        UnionFind uf = new UnionFind(10);
        int[][] links = {{0, 1}, {1, 2}, {3, 4}, {5, 6}, {6, 7}, {7, 5}, {2, 0}, {8, 9}};
        for (int[] l : links) {
            System.out.println("union(" + l[0] + "," + l[1] + ") -> " + uf.union(l[0], l[1]));
        }
        System.out.println("components: " + uf.components());
        System.out.println("connected(0,2)=" + uf.connected(0, 2) + " connected(2,3)=" + uf.connected(2, 3));

        Map<Integer, List<Integer>> groups = new TreeMap<>();
        for (int i = 0; i < 10; i++) {
            groups.computeIfAbsent(uf.find(i), k -> new ArrayList<>()).add(i);
        }
        System.out.println("groups: " + groups.values().stream().sorted(Comparator.comparing(g -> g.get(0))).toList());

        // Kruskal's minimum spanning tree on a small weighted graph
        List<Edge> edges = List.of(
                new Edge(0, 1, 4), new Edge(0, 2, 3), new Edge(1, 2, 1), new Edge(1, 3, 2),
                new Edge(2, 3, 4), new Edge(3, 4, 2), new Edge(4, 5, 6), new Edge(3, 5, 7));
        List<Edge> mst = kruskal(6, edges);
        System.out.println("MST edges: " + mst);
        System.out.println("MST weight: " + mst.stream().mapToInt(Edge::weight).sum());

        // Self-check: same partition as a brute-force flood fill on random edges
        java.util.Random rnd = new java.util.Random(16);
        boolean ok = true;
        for (int t = 0; t < 200; t++) {
            int n = 1 + rnd.nextInt(30);
            List<int[]> pairs = new ArrayList<>();
            UnionFind u = new UnionFind(n);
            for (int k = rnd.nextInt(n + 5); k > 0; k--) {
                int a = rnd.nextInt(n), b = rnd.nextInt(n);
                pairs.add(new int[] {a, b});
                u.union(a, b);
            }
            int[] label = floodLabels(n, pairs);
            for (int a = 0; a < n; a++) {
                for (int b = 0; b < n; b++) {
                    ok &= (label[a] == label[b]) == u.connected(a, b);
                }
            }
            ok &= u.components() == Arrays.stream(label).max().orElse(-1) + 1;
        }
        System.out.println("200 random graphs match flood fill: " + ok);
    }
}

/* Output:
union(0,1) -> true
union(1,2) -> true
union(3,4) -> true
union(5,6) -> true
union(6,7) -> true
union(7,5) -> false
union(2,0) -> false
union(8,9) -> true
components: 4
connected(0,2)=true connected(2,3)=false
groups: [[0, 1, 2], [3, 4], [5, 6, 7], [8, 9]]
MST edges: [Edge[u=1, v=2, weight=1], Edge[u=1, v=3, weight=2], Edge[u=3, v=4, weight=2], Edge[u=0, v=2, weight=3], Edge[u=4, v=5, weight=6]]
MST weight: 14
200 random graphs match flood fill: true
*/
