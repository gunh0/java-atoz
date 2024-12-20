// Dynamic programming solves a problem by combining answers to overlapping
// subproblems, each computed once and stored in a table. Three classics:
//  - longest common subsequence: lcs[i][j] for prefixes a[0..i), b[0..j); walk
//    the table backwards to recover one subsequence (the basis of diff tools)
//  - 0/1 knapsack: best value with capacity w using the first i items; a 1-D
//    table iterated over w downwards so each item is used at most once
//  - edit (Levenshtein) distance: minimum inserts, deletes and substitutions,
//    handy for "did you mean" suggestions
// Each one is checked against a brute-force search on small random inputs.
import java.util.Random;

public class DpClassics {
    static String lcs(String a, String b) {
        int n = a.length(), m = b.length();
        int[][] t = new int[n + 1][m + 1];
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                t[i][j] = a.charAt(i - 1) == b.charAt(j - 1)
                        ? t[i - 1][j - 1] + 1
                        : Math.max(t[i - 1][j], t[i][j - 1]);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = n, j = m; i > 0 && j > 0; ) {
            if (a.charAt(i - 1) == b.charAt(j - 1)) {
                sb.append(a.charAt(i - 1));
                i--;
                j--;
            } else if (t[i - 1][j] >= t[i][j - 1]) {
                i--;
            } else {
                j--;
            }
        }
        return sb.reverse().toString();
    }

    static int knapsack(int[] weight, int[] value, int capacity) {
        int[] best = new int[capacity + 1];
        for (int i = 0; i < weight.length; i++) {
            for (int w = capacity; w >= weight[i]; w--) { // downwards: item i used once
                best[w] = Math.max(best[w], best[w - weight[i]] + value[i]);
            }
        }
        return best[capacity];
    }

    static int editDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitute = prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                cur[j] = Math.min(substitute, Math.min(prev[j] + 1, cur[j - 1] + 1));
            }
            int[] swap = prev;
            prev = cur;
            cur = swap;
        }
        return prev[b.length()];
    }

    // ---- brute-force references ----

    static boolean isSubsequence(String s, String of) {
        int k = 0;
        for (int i = 0; i < of.length() && k < s.length(); i++) {
            if (of.charAt(i) == s.charAt(k)) {
                k++;
            }
        }
        return k == s.length();
    }

    static int lcsBrute(String a, String b) { // every subsequence of a
        int best = 0;
        for (int mask = 0; mask < 1 << a.length(); mask++) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < a.length(); i++) {
                if ((mask & 1 << i) != 0) {
                    sb.append(a.charAt(i));
                }
            }
            if (sb.length() > best && isSubsequence(sb.toString(), b)) {
                best = sb.length();
            }
        }
        return best;
    }

    static int knapsackBrute(int[] weight, int[] value, int capacity) {
        int best = 0;
        for (int mask = 0; mask < 1 << weight.length; mask++) {
            int w = 0, v = 0;
            for (int i = 0; i < weight.length; i++) {
                if ((mask & 1 << i) != 0) {
                    w += weight[i];
                    v += value[i];
                }
            }
            if (w <= capacity) {
                best = Math.max(best, v);
            }
        }
        return best;
    }

    static int editBrute(String a, String b) { // plain recursion, exponential
        if (a.isEmpty()) return b.length();
        if (b.isEmpty()) return a.length();
        String ra = a.substring(1), rb = b.substring(1);
        if (a.charAt(0) == b.charAt(0)) return editBrute(ra, rb);
        return 1 + Math.min(editBrute(ra, rb), Math.min(editBrute(ra, b), editBrute(a, rb)));
    }

    public static void main(String[] args) {
        System.out.println("LCS(AGGTAB, GXTXAYB) = " + lcs("AGGTAB", "GXTXAYB"));
        System.out.println("LCS(ABCBDAB, BDCABA) = " + lcs("ABCBDAB", "BDCABA"));

        int[] weight = {1, 3, 4, 5};
        int[] value = {1, 4, 5, 7};
        System.out.println("knapsack weights 1,3,4,5 values 1,4,5,7 capacity 7 = " + knapsack(weight, value, 7));

        for (String[] pair : new String[][] {{"kitten", "sitting"}, {"flaw", "lawn"}, {"passwd", "password"}, {"", "abc"}}) {
            System.out.println("edit(" + pair[0] + ", " + pair[1] + ") = " + editDistance(pair[0], pair[1]));
        }

        // "did you mean": closest known command by edit distance
        String typed = "comit";
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String cmd : new String[] {"commit", "config", "clone", "checkout", "cherry-pick"}) {
            int d = editDistance(typed, cmd);
            if (d < bestDistance) {
                best = cmd;
                bestDistance = d;
            }
        }
        System.out.println("'" + typed + "' -> did you mean '" + best + "'? (distance " + bestDistance + ")");

        Random rnd = new Random(24);
        boolean ok = true;
        for (int t = 0; t < 300; t++) {
            String a = random(rnd, rnd.nextInt(9)), b = random(rnd, rnd.nextInt(9));
            String l = lcs(a, b);
            ok &= l.length() == lcsBrute(a, b) && isSubsequence(l, a) && isSubsequence(l, b);
            ok &= editDistance(a, b) == editBrute(a, b);
            int n = rnd.nextInt(11);
            int[] w = rnd.ints(n, 1, 15).toArray(), v = rnd.ints(n, 1, 30).toArray();
            int cap = rnd.nextInt(40);
            ok &= knapsack(w, v, cap) == knapsackBrute(w, v, cap);
        }
        System.out.println("300 random cases match brute force: " + ok);
    }

    static String random(Random rnd, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append("abc".charAt(rnd.nextInt(3)));
        }
        return sb.toString();
    }
}

/* Output:
LCS(AGGTAB, GXTXAYB) = GTAB
LCS(ABCBDAB, BDCABA) = BCBA
knapsack weights 1,3,4,5 values 1,4,5,7 capacity 7 = 9
edit(kitten, sitting) = 3
edit(flaw, lawn) = 2
edit(passwd, password) = 2
edit(, abc) = 3
'comit' -> did you mean 'commit'? (distance 1)
300 random cases match brute force: true
*/
