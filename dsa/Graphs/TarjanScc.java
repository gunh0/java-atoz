// A strongly connected component (SCC) of a directed graph is a maximal set of
// vertices that can all reach each other. Collapsing each SCC to one vertex
// leaves a DAG (the condensation), which is how cyclic dependency graphs are
// analysed: module cycles, deadlock detection in wait-for graphs, 2-SAT.
// Tarjan's algorithm finds all SCCs in one DFS, O(V + E). Each vertex gets a
// discovery index and a low-link: the smallest index reachable from its DFS
// subtree using tree edges plus one edge back to a vertex still on the stack.
// Vertices are pushed on a stack when discovered; when a vertex finishes with
// low == index it is the root of an SCC, and everything above it on the stack
// is popped as that component. SCCs come out in reverse topological order of
// the condensation. This version is iterative (an explicit frame stack), so a
// long chain cannot overflow the thread stack. Kosaraju's algorithm (two DFS
// passes, the second on the reversed graph) is used as the reference.
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

public class TarjanScc {
    // Returns comp[v] = component id; ids are assigned in the order SCCs complete
    static int[] tarjan(List<List<Integer>> g) {
        int n = g.size();
        int[] index = new int[n], low = new int[n], comp = new int[n];
        Arrays.fill(index, -1);
        boolean[] onStack = new boolean[n];
        Deque<Integer> stack = new ArrayDeque<>();
        int counter = 0, comps = 0;
        int[] edgePos = new int[n];          // next edge to look at, per vertex
        Deque<Integer> frames = new ArrayDeque<>(); // the simulated recursion
        for (int root = 0; root < n; root++) {
            if (index[root] != -1) continue;
            frames.push(root);
            index[root] = low[root] = counter++;
            stack.push(root);
            onStack[root] = true;
            while (!frames.isEmpty()) {
                int v = frames.peek();
                if (edgePos[v] < g.get(v).size()) {
                    int w = g.get(v).get(edgePos[v]++);
                    if (index[w] == -1) {       // tree edge: "recurse" into w
                        index[w] = low[w] = counter++;
                        stack.push(w);
                        onStack[w] = true;
                        frames.push(w);
                    } else if (onStack[w]) {    // edge back into the current SCC candidate
                        low[v] = Math.min(low[v], index[w]);
                    }
                    continue;
                }
                frames.pop();                   // v is finished: "return" to its parent
                if (low[v] == index[v]) {
                    int w;
                    do {
                        w = stack.pop();
                        onStack[w] = false;
                        comp[w] = comps;
                    } while (w != v);
                    comps++;
                }
                if (!frames.isEmpty()) {
                    int parent = frames.peek();
                    low[parent] = Math.min(low[parent], low[v]);
                }
            }
        }
        return comp;
    }

    // Reference: Kosaraju. DFS finish order on g, then DFS on the reversed graph in reverse finish order
    static int[] kosaraju(List<List<Integer>> g) {
        int n = g.size();
        boolean[] seen = new boolean[n];
        List<Integer> order = new ArrayList<>();
        for (int s = 0; s < n; s++) {
            if (!seen[s]) finishOrder(g, s, seen, order);
        }
        List<List<Integer>> rev = new ArrayList<>();
        for (int i = 0; i < n; i++) rev.add(new ArrayList<>());
        for (int u = 0; u < n; u++) for (int v : g.get(u)) rev.get(v).add(u);
        int[] comp = new int[n];
        Arrays.fill(comp, -1);
        int c = 0;
        for (int i = n - 1; i >= 0; i--) {
            int s = order.get(i);
            if (comp[s] != -1) continue;
            Deque<Integer> st = new ArrayDeque<>(List.of(s));
            comp[s] = c;
            while (!st.isEmpty()) {
                int u = st.pop();
                for (int v : rev.get(u)) {
                    if (comp[v] == -1) {
                        comp[v] = c;
                        st.push(v);
                    }
                }
            }
            c++;
        }
        return comp;
    }

    static void finishOrder(List<List<Integer>> g, int s, boolean[] seen, List<Integer> order) {
        Deque<int[]> st = new ArrayDeque<>(); // {vertex, next edge}
        st.push(new int[] {s, 0});
        seen[s] = true;
        while (!st.isEmpty()) {
            int[] top = st.peek();
            if (top[1] < g.get(top[0]).size()) {
                int v = g.get(top[0]).get(top[1]++);
                if (!seen[v]) {
                    seen[v] = true;
                    st.push(new int[] {v, 0});
                }
            } else {
                order.add(st.pop()[0]);
            }
        }
    }

    // Component ids differ between algorithms, so compare the partitions as sorted sets
    static TreeSet<String> partition(int[] comp) {
        List<TreeSet<Integer>> groups = new ArrayList<>();
        for (int v = 0; v < comp.length; v++) {
            while (groups.size() <= comp[v]) groups.add(new TreeSet<>());
            groups.get(comp[v]).add(v);
        }
        TreeSet<String> out = new TreeSet<>();
        groups.forEach(gr -> out.add(gr.toString()));
        return out;
    }

    static List<List<Integer>> graph(int n, int[][] edges) {
        List<List<Integer>> g = new ArrayList<>();
        for (int i = 0; i < n; i++) g.add(new ArrayList<>());
        for (int[] e : edges) g.get(e[0]).add(e[1]);
        return g;
    }

    public static void main(String[] args) {
        // Module dependencies: an edge a -> b means "a imports b"
        String[] mods = {"app", "web", "auth", "user", "db", "log", "cfg", "util"};
        int[][] deps = {{0, 1}, {1, 2}, {2, 3}, {3, 1}, {2, 4}, {4, 5}, {5, 6}, {6, 5}, {3, 7}, {4, 7}};
        List<List<Integer>> g = graph(mods.length, deps);
        int[] comp = tarjan(g);
        int count = Arrays.stream(comp).max().orElse(-1) + 1;
        List<List<String>> sccs = new ArrayList<>();
        for (int c = 0; c < count; c++) sccs.add(new ArrayList<>());
        for (int v = 0; v < mods.length; v++) sccs.get(comp[v]).add(mods[v]);
        for (int c = 0; c < count; c++) {
            System.out.println("SCC " + c + ": " + sccs.get(c) + (sccs.get(c).size() > 1 ? "  <- import cycle" : ""));
        }
        // Condensation: edges between different components, deduplicated
        TreeSet<String> dag = new TreeSet<>();
        for (int[] e : deps) {
            if (comp[e[0]] != comp[e[1]]) dag.add(comp[e[0]] + "->" + comp[e[1]]);
        }
        System.out.println("condensation DAG: " + dag + " (every edge goes from a higher id to a lower one)");

        // Self-check against Kosaraju on random graphs, plus a 200000-vertex chain
        Random rnd = new Random(12);
        boolean ok = true;
        for (int t = 0; t < 300; t++) {
            int n = 1 + rnd.nextInt(40);
            int[][] es = new int[rnd.nextInt(n * 3)][];
            for (int k = 0; k < es.length; k++) es[k] = new int[] {rnd.nextInt(n), rnd.nextInt(n)};
            List<List<Integer>> rg = graph(n, es);
            ok &= partition(tarjan(rg)).equals(partition(kosaraju(rg)));
        }
        System.out.println("300 random graphs match Kosaraju: " + ok);
        int n = 200_000;
        int[][] ring = new int[n][];
        for (int i = 0; i < n; i++) ring[i] = new int[] {i, (i + 1) % n};
        int[] rc = tarjan(graph(n, ring));
        System.out.println("ring of " + n + " vertices: " + (Arrays.stream(rc).max().orElse(-1) + 1)
                + " SCC, no StackOverflowError");
    }
}

/* Output:
SCC 0: [util]
SCC 1: [log, cfg]  <- import cycle
SCC 2: [db]
SCC 3: [web, auth, user]  <- import cycle
SCC 4: [app]
condensation DAG: [2->0, 2->1, 3->0, 3->2, 4->3] (every edge goes from a higher id to a lower one)
300 random graphs match Kosaraju: true
ring of 200000 vertices: 1 SCC, no StackOverflowError
*/
