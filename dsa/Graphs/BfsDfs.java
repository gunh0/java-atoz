// Breadth-first and depth-first search over an adjacency-list graph.
// BFS explores in rings of increasing distance using a FIFO queue, so the first
// time it reaches a vertex is along a shortest path (counting edges); keeping a
// parent array lets us rebuild that path. DFS goes as deep as possible first,
// using recursion or an explicit stack; colouring vertices white/grey/black during
// DFS detects cycles in a directed graph (a grey neighbour is a back edge).
// Both run in O(V + E). Neighbour lists are sorted so the traversal order is
// deterministic.
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public class BfsDfs {
    static final class Graph {
        final List<List<Integer>> adj = new ArrayList<>();
        final boolean directed;

        Graph(int n, boolean directed) {
            this.directed = directed;
            for (int i = 0; i < n; i++) {
                adj.add(new ArrayList<>());
            }
        }

        Graph edge(int u, int v) {
            adj.get(u).add(v);
            Collections.sort(adj.get(u));
            if (!directed) {
                adj.get(v).add(u);
                Collections.sort(adj.get(v));
            }
            return this;
        }

        int size() {
            return adj.size();
        }
    }

    static List<Integer> bfsOrder(Graph g, int start, int[] dist, int[] parent) {
        Arrays.fill(dist, -1);
        Arrays.fill(parent, -1);
        List<Integer> order = new ArrayList<>();
        Deque<Integer> queue = new ArrayDeque<>();
        dist[start] = 0;
        queue.add(start);
        while (!queue.isEmpty()) {
            int u = queue.poll();
            order.add(u);
            for (int v : g.adj.get(u)) {
                if (dist[v] == -1) {
                    dist[v] = dist[u] + 1;
                    parent[v] = u;
                    queue.add(v);
                }
            }
        }
        return order;
    }

    static List<Integer> path(int[] parent, int target) {
        List<Integer> p = new ArrayList<>();
        for (int v = target; v != -1; v = parent[v]) {
            p.add(v);
        }
        Collections.reverse(p);
        return p;
    }

    static void dfsRecursive(Graph g, int u, boolean[] seen, List<Integer> order) {
        seen[u] = true;
        order.add(u);
        for (int v : g.adj.get(u)) {
            if (!seen[v]) {
                dfsRecursive(g, v, seen, order);
            }
        }
    }

    // Explicit stack; neighbours pushed in reverse so the visit order equals the recursive one
    static List<Integer> dfsIterative(Graph g, int start) {
        boolean[] seen = new boolean[g.size()];
        List<Integer> order = new ArrayList<>();
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(start);
        while (!stack.isEmpty()) {
            int u = stack.pop();
            if (seen[u]) {
                continue;
            }
            seen[u] = true;
            order.add(u);
            List<Integer> next = g.adj.get(u);
            for (int i = next.size() - 1; i >= 0; i--) {
                if (!seen[next.get(i)]) {
                    stack.push(next.get(i));
                }
            }
        }
        return order;
    }

    // 0 = white (unvisited), 1 = grey (on the current path), 2 = black (finished)
    static boolean hasCycle(Graph g) {
        int[] color = new int[g.size()];
        for (int s = 0; s < g.size(); s++) {
            if (color[s] == 0 && hasCycleFrom(g, s, color)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCycleFrom(Graph g, int u, int[] color) {
        color[u] = 1;
        for (int v : g.adj.get(u)) {
            if (color[v] == 1 || (color[v] == 0 && hasCycleFrom(g, v, color))) {
                return true;
            }
        }
        color[u] = 2;
        return false;
    }

    static int countComponents(Graph g) {
        boolean[] seen = new boolean[g.size()];
        int count = 0;
        for (int s = 0; s < g.size(); s++) {
            if (!seen[s]) {
                dfsRecursive(g, s, seen, new ArrayList<>());
                count++;
            }
        }
        return count;
    }

    public static void main(String[] args) {
        //   0 - 1 - 3 - 5
        //   |   |   |
        //   2 - 4   6      7 - 8
        Graph g = new Graph(9, false)
                .edge(0, 1).edge(0, 2).edge(1, 3).edge(1, 4).edge(2, 4)
                .edge(3, 5).edge(3, 6).edge(7, 8);
        int[] dist = new int[g.size()];
        int[] parent = new int[g.size()];
        System.out.println("BFS from 0:     " + bfsOrder(g, 0, dist, parent));
        System.out.println("distances:      " + Arrays.toString(dist));
        System.out.println("path 0 -> 6:    " + path(parent, 6));
        List<Integer> rec = new ArrayList<>();
        dfsRecursive(g, 0, new boolean[g.size()], rec);
        System.out.println("DFS recursive:  " + rec);
        System.out.println("DFS iterative:  " + dfsIterative(g, 0));
        System.out.println("components:     " + countComponents(g));

        Graph dag = new Graph(4, true).edge(0, 1).edge(1, 2).edge(0, 2).edge(2, 3);
        Graph cyclic = new Graph(4, true).edge(0, 1).edge(1, 2).edge(2, 3).edge(3, 1);
        System.out.println("cycle in dag: " + hasCycle(dag) + ", cycle in cyclic: " + hasCycle(cyclic));

        // Self-check: BFS distances on a grid graph equal Manhattan distance from the corner
        int w = 7, h = 5;
        Graph grid = new Graph(w * h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (x + 1 < w) grid.edge(y * w + x, y * w + x + 1);
                if (y + 1 < h) grid.edge(y * w + x, (y + 1) * w + x);
            }
        }
        int[] gd = new int[grid.size()];
        bfsOrder(grid, 0, gd, new int[grid.size()]);
        boolean ok = true;
        for (int v = 0; v < grid.size(); v++) {
            ok &= gd[v] == v % w + v / w;
        }
        List<Integer> r = new ArrayList<>();
        dfsRecursive(grid, 0, new boolean[grid.size()], r);
        ok &= r.equals(dfsIterative(grid, 0)) && r.size() == w * h;
        System.out.println("grid BFS = Manhattan distance, DFS orders agree: " + ok);
    }
}

/* Output:
BFS from 0:     [0, 1, 2, 3, 4, 5, 6]
distances:      [0, 1, 1, 2, 2, 3, 3, -1, -1]
path 0 -> 6:    [0, 1, 3, 6]
DFS recursive:  [0, 1, 3, 5, 6, 4, 2]
DFS iterative:  [0, 1, 3, 5, 6, 4, 2]
components:     2
cycle in dag: false, cycle in cyclic: true
grid BFS = Manhattan distance, DFS orders agree: true
*/
