// A* is Dijkstra with a sense of direction. Dijkstra expands vertices in order
// of g(v), the cost from the start, so it searches in a widening circle. A*
// orders them by f(v) = g(v) + h(v), where h estimates the remaining cost to
// the goal. If h never overestimates (it is "admissible") the first time the
// goal is taken from the queue its cost is optimal; if it is also consistent,
// h(u) <= cost(u, v) + h(v) for every edge, each vertex is expanded at most
// once, as in Dijkstra. On a 4-connected grid with unit steps the Manhattan
// distance |dx| + |dy| is consistent. With h = 0 A* is exactly Dijkstra; the
// better h is, the fewer vertices it expands. Ties on f are broken toward the
// larger g (smaller h), which pushes the search along the straight line
// instead of filling every equally good cell. Worst case remains
// O(E log V); the practical win is the number of expanded vertices.
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

public class AStarGrid {
    record Search(int cost, int expanded, int[] prev) { }

    static final int[][] STEPS = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};

    // useHeuristic = false turns A* into Dijkstra on the same code path
    static Search search(char[][] grid, int start, int goal, boolean useHeuristic) {
        int rows = grid.length, cols = grid[0].length, n = rows * cols;
        int gr = goal / cols, gc = goal % cols;
        int[] g = new int[n];
        int[] prev = new int[n];
        boolean[] closed = new boolean[n];
        Arrays.fill(g, Integer.MAX_VALUE);
        Arrays.fill(prev, -1);
        // entries: {f, h, cell}; smaller f first, then smaller h (= larger g), then cell index
        PriorityQueue<int[]> open = new PriorityQueue<>((a, b) ->
                a[0] != b[0] ? Integer.compare(a[0], b[0])
                        : a[1] != b[1] ? Integer.compare(a[1], b[1]) : Integer.compare(a[2], b[2]));
        g[start] = 0;
        int h0 = useHeuristic ? Math.abs(start / cols - gr) + Math.abs(start % cols - gc) : 0;
        open.add(new int[] {h0, h0, start});
        int expanded = 0;
        while (!open.isEmpty()) {
            int cell = open.poll()[2];
            if (closed[cell]) continue; // stale duplicate
            closed[cell] = true;
            expanded++;
            if (cell == goal) return new Search(g[goal], expanded, prev);
            int r = cell / cols, c = cell % cols;
            for (int[] s : STEPS) {
                int nr = r + s[0], nc = c + s[1];
                if (nr < 0 || nc < 0 || nr >= rows || nc >= cols || grid[nr][nc] == '#') continue;
                int next = nr * cols + nc;
                if (g[cell] + 1 < g[next]) {
                    g[next] = g[cell] + 1;
                    prev[next] = cell;
                    int h = useHeuristic ? Math.abs(nr - gr) + Math.abs(nc - gc) : 0;
                    open.add(new int[] {g[next] + h, h, next});
                }
            }
        }
        return new Search(-1, expanded, prev);
    }

    // Reference: plain BFS distance (all steps cost 1)
    static int bfs(char[][] grid, int start, int goal) {
        int rows = grid.length, cols = grid[0].length;
        int[] dist = new int[rows * cols];
        Arrays.fill(dist, -1);
        dist[start] = 0;
        Deque<Integer> q = new ArrayDeque<>(List.of(start));
        while (!q.isEmpty()) {
            int cell = q.poll();
            int r = cell / cols, c = cell % cols;
            for (int[] s : STEPS) {
                int nr = r + s[0], nc = c + s[1];
                if (nr < 0 || nc < 0 || nr >= rows || nc >= cols || grid[nr][nc] == '#') continue;
                int next = nr * cols + nc;
                if (dist[next] == -1) {
                    dist[next] = dist[cell] + 1;
                    q.add(next);
                }
            }
        }
        return dist[goal];
    }

    static int find(char[][] grid, char ch) {
        for (int r = 0; r < grid.length; r++)
            for (int c = 0; c < grid[r].length; c++)
                if (grid[r][c] == ch) return r * grid[r].length + c;
        throw new IllegalArgumentException("no " + ch);
    }

    public static void main(String[] args) {
        String map = """
                S.........#.........
                ..######..#..#####..
                .......#..#......#..
                .#####.#..#####..#..
                .#.....#......#.....
                .#.#######.......#..
                .#........#####..#..
                .########.#......#..
                ..........#..#####.G
                """;
        char[][] grid = map.lines().map(String::toCharArray).toArray(char[][]::new);
        int start = find(grid, 'S'), goal = find(grid, 'G');
        Search astar = search(grid, start, goal, true);
        Search dijkstra = search(grid, start, goal, false);
        for (int cell = astar.prev()[goal]; cell != start; cell = astar.prev()[cell]) {
            grid[cell / grid[0].length][cell % grid[0].length] = '*';
        }
        for (char[] row : grid) System.out.println(new String(row));
        System.out.println("A* cost " + astar.cost() + ", expanded " + astar.expanded() + " cells");
        System.out.println("Dijkstra cost " + dijkstra.cost() + ", expanded " + dijkstra.expanded() + " cells");
        System.out.println("BFS distance " + bfs(grid, start, goal));

        // Self-check on random mazes: A* cost equals BFS (or both unreachable), never expands more than Dijkstra
        Random rnd = new Random(23);
        boolean ok = true;
        long aExp = 0, dExp = 0;
        for (int t = 0; t < 300; t++) {
            int rows = 5 + rnd.nextInt(40), cols = 5 + rnd.nextInt(40);
            char[][] gmap = new char[rows][cols];
            for (char[] row : gmap)
                for (int c = 0; c < cols; c++) row[c] = rnd.nextInt(100) < 28 ? '#' : '.';
            int s = rnd.nextInt(rows * cols), e = rnd.nextInt(rows * cols);
            gmap[s / cols][s % cols] = '.';
            gmap[e / cols][e % cols] = '.';
            Search a = search(gmap, s, e, true);
            Search d = search(gmap, s, e, false);
            ok &= a.cost() == bfs(gmap, s, e) && d.cost() == a.cost() && a.expanded() <= d.expanded();
            aExp += a.expanded();
            dExp += d.expanded();
        }
        System.out.println("300 random mazes: A* cost = BFS = Dijkstra, never more expansions: " + ok);
        System.out.printf("expanded in total: A* %d, Dijkstra %d%n", aExp, dExp);
    }
}

/* Output:
S*********#.........
..######.*#..#####..
.......#.*#......#..
.#####.#.*#####..#..
.#.....#.*****#.****
.#.#######...****#.*
.#........#####..#.*
.########.#......#.*
..........#..#####.G
A* cost 29, expanded 68 cells
Dijkstra cost 29, expanded 114 cells
BFS distance 29
300 random mazes: A* cost = BFS = Dijkstra, never more expansions: true
expanded in total: A* 17541, Dijkstra 61726
*/
