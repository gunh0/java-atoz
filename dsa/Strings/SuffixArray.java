// A suffix array lists the starting positions of all suffixes of a string in
// sorted order; for "banana" it is [5, 3, 1, 0, 4, 2] (a, ana, anana, banana,
// na, nana). It is a compact stand-in for a suffix tree: every substring is a
// prefix of some suffix, so all occurrences of a pattern form one contiguous
// block that binary search finds in O(m log n). Prefix doubling builds it in
// O(n log n): rank suffixes by their first 1 character, then by their first
// 2, 4, 8, ... characters, where the rank by 2k characters is the pair
// (rank of the first k, rank of the next k), sorted with two counting-sort
// passes (radix sort). Kasai's algorithm then fills the LCP array, the length
// of the longest common prefix of each pair of neighbours, in O(n), using the
// fact that the LCP drops by at most 1 when moving from suffix i to i+1.
// From these two arrays: the longest repeated substring is the max LCP, and
// the number of distinct substrings is n(n+1)/2 - sum(LCP).
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class SuffixArray {
    static int[] build(String s) {
        int n = s.length();
        if (n == 0) return new int[0];
        int[] sa = new int[n], rank = new int[n], tmp = new int[n], tmpSa = new int[n];
        for (int i = 0; i < n; i++) {
            sa[i] = i;
            rank[i] = s.charAt(i);
        }
        int maxRank = Math.max(n, Character.MAX_VALUE + 1);
        for (int k = 1; ; k <<= 1) {
            // Radix sort by (rank[i], rank[i + k]); a missing second half (-1) sorts first
            countingSort(sa, tmpSa, rank, k, n, maxRank + 1, true);  // secondary key
            countingSort(tmpSa, sa, rank, 0, n, maxRank + 1, false); // primary key, stable
            tmp[sa[0]] = 0;
            for (int i = 1; i < n; i++) {
                int a = sa[i - 1], b = sa[i];
                boolean same = rank[a] == rank[b] && second(rank, a, k, n) == second(rank, b, k, n);
                tmp[b] = tmp[a] + (same ? 0 : 1);
            }
            System.arraycopy(tmp, 0, rank, 0, n);
            if (rank[sa[n - 1]] == n - 1) {
                return sa; // all ranks distinct: fully sorted
            }
            maxRank = n;
        }
    }

    static int second(int[] rank, int i, int k, int n) {
        return i + k < n ? rank[i + k] : -1;
    }

    // Stable counting sort of `from` into `to` by rank[i + offset] (+1 so that -1 maps to bucket 0)
    static void countingSort(int[] from, int[] to, int[] rank, int offset, int n, int buckets, boolean useOffset) {
        int[] count = new int[buckets + 1];
        for (int i = 0; i < n; i++) {
            count[key(rank, from[i], offset, n, useOffset)]++;
        }
        for (int b = 0, sum = 0; b <= buckets; b++) {
            int c = count[b];
            count[b] = sum;
            sum += c;
        }
        for (int i = 0; i < n; i++) {
            to[count[key(rank, from[i], offset, n, useOffset)]++] = from[i];
        }
    }

    static int key(int[] rank, int i, int offset, int n, boolean useOffset) {
        return (useOffset ? second(rank, i, offset, n) : rank[i]) + 1;
    }

    // Kasai: lcp[i] = LCP of suffixes sa[i-1] and sa[i]; lcp[0] = 0
    static int[] lcp(String s, int[] sa) {
        int n = s.length();
        int[] pos = new int[n], lcp = new int[n];
        for (int i = 0; i < n; i++) pos[sa[i]] = i;
        int h = 0;
        for (int i = 0; i < n; i++) { // suffixes in text order, not sorted order
            if (pos[i] > 0) {
                int j = sa[pos[i] - 1];
                while (i + h < n && j + h < n && s.charAt(i + h) == s.charAt(j + h)) h++;
                lcp[pos[i]] = h;
                if (h > 0) h--; // the next suffix shares at least h-1 characters with its neighbour
            } else {
                h = 0;
            }
        }
        return lcp;
    }

    // All occurrences of p: one contiguous block of the suffix array
    static List<Integer> occurrences(String s, int[] sa, String p) {
        int lo = lowerBound(s, sa, p, false), hi = lowerBound(s, sa, p, true);
        List<Integer> out = new ArrayList<>();
        for (int i = lo; i < hi; i++) out.add(sa[i]);
        out.sort(null);
        return out;
    }

    // First index whose suffix is >= p (or, with past = true, does not start with p and is > p)
    static int lowerBound(String s, int[] sa, String p, boolean past) {
        int lo = 0, hi = sa.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            String prefix = s.substring(sa[mid], Math.min(s.length(), sa[mid] + p.length()));
            int c = prefix.compareTo(p);
            if (c < 0 || (past && c == 0)) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    static int[] naive(String s) {
        Integer[] idx = new Integer[s.length()];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        Arrays.sort(idx, (a, b) -> s.substring(a).compareTo(s.substring(b)));
        return Arrays.stream(idx).mapToInt(Integer::intValue).toArray();
    }

    public static void main(String[] args) {
        String s = "banana";
        int[] sa = build(s);
        int[] lcp = lcp(s, sa);
        for (int i = 0; i < sa.length; i++) {
            System.out.printf("sa[%d] = %d  lcp %d  %s%n", i, sa[i], lcp[i], s.substring(sa[i]));
        }

        String log = "user=alice action=login user=bob action=login user=alice action=logout";
        int[] lsa = build(log);
        int[] llcp = lcp(log, lsa);
        int best = 0;
        for (int i = 1; i < llcp.length; i++) if (llcp[i] > llcp[best]) best = i;
        System.out.println("longest repeated substring: \"" + log.substring(lsa[best], lsa[best] + llcp[best]) + "\"");
        System.out.println("\"user=\" at " + occurrences(log, lsa, "user=")
                + ", \"action=log\" at " + occurrences(log, lsa, "action=log"));
        long distinct = (long) s.length() * (s.length() + 1) / 2 - Arrays.stream(lcp).sum();
        System.out.println("distinct substrings of banana: " + distinct);

        // Self-check: suffix array against sorting substrings, distinct count against a HashSet
        Random rnd = new Random(26);
        boolean ok = true;
        for (int t = 0; t < 500; t++) {
            int len = rnd.nextInt(60);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < len; i++) sb.append((char) ('a' + rnd.nextInt(t % 3 + 1)));
            String r = sb.toString();
            int[] a = build(r);
            ok &= Arrays.equals(a, naive(r));
            Set<String> subs = new HashSet<>();
            for (int i = 0; i < len; i++) for (int j = i + 1; j <= len; j++) subs.add(r.substring(i, j));
            ok &= subs.size() == (long) len * (len + 1) / 2 - Arrays.stream(lcp(r, a)).sum();
        }
        System.out.println("500 random strings match naive sorting and a HashSet of substrings: " + ok);
        String big = "ab".repeat(100_000) + "c";
        int[] bigSa = build(big);
        System.out.println("n = " + big.length() + ": first suffix starts at " + bigSa[0]
                + ", max LCP " + Arrays.stream(lcp(big, bigSa)).max().orElse(0));
    }
}

/* Output:
sa[0] = 5  lcp 0  a
sa[1] = 3  lcp 1  ana
sa[2] = 1  lcp 3  anana
sa[3] = 0  lcp 0  banana
sa[4] = 4  lcp 0  na
sa[5] = 2  lcp 2  nana
longest repeated substring: "user=alice action=log"
"user=" at [0, 24, 46], "action=log" at [11, 33, 57]
distinct substrings of banana: 15
500 random strings match naive sorting and a HashSet of substrings: true
n = 200001: first suffix starts at 0, max LCP 199998
*/
