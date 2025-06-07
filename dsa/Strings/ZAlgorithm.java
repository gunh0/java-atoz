// The Z-array of a string s gives, for every position i, the length of the
// longest substring starting at i that is also a prefix of s (z[0] is taken
// as 0 here). Computed naively that is O(n^2); the Z-algorithm does it in O(n)
// by keeping the rightmost "Z-box" [l, r) found so far, a window known to equal
// s[0 .. r-l). For i inside the box, s[i..r) mirrors s[i-l..r-l), so z[i] is
// at least min(z[i-l], r-i) for free; only characters beyond r are compared,
// and each comparison moves r forward, hence linear time overall.
// Pattern search: build pattern + sep + text with a separator that occurs in
// neither, so no match can run across it; every position with z == m is an
// occurrence. Same O(n + m) as KMP, with a simpler invariant. Other uses:
// the string's periods (i is a period if i + z[i] == n), and counting how many
// times each prefix occurs.
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class ZAlgorithm {
    static int[] zArray(CharSequence s) {
        int n = s.length();
        int[] z = new int[n];
        int l = 0, r = 0; // current Z-box is [l, r)
        for (int i = 1; i < n; i++) {
            if (i < r) {
                z[i] = Math.min(r - i, z[i - l]); // reuse what the box already tells us
            }
            while (i + z[i] < n && s.charAt(z[i]) == s.charAt(i + z[i])) {
                z[i]++;
            }
            if (i + z[i] > r) {
                l = i;
                r = i + z[i];
            }
        }
        return z;
    }

    static int[] naiveZ(String s) {
        int[] z = new int[s.length()];
        for (int i = 1; i < s.length(); i++) {
            while (i + z[i] < s.length() && s.charAt(z[i]) == s.charAt(i + z[i])) z[i]++;
        }
        return z;
    }

    // All start indexes of pattern in text; sep must not occur in either
    static List<Integer> search(String pattern, String text, char sep) {
        int m = pattern.length();
        int[] z = zArray(pattern + sep + text);
        List<Integer> hits = new ArrayList<>();
        for (int i = m + 1; i < z.length; i++) {
            if (z[i] == m) hits.add(i - m - 1);
        }
        return hits;
    }

    static List<Integer> indexOfAll(String pattern, String text) {
        List<Integer> hits = new ArrayList<>();
        for (int i = text.indexOf(pattern); i >= 0; i = text.indexOf(pattern, i + 1)) hits.add(i);
        return hits;
    }

    // Smallest p with s[i] == s[i+p] everywhere, i.e. s is a prefix of (s[0..p))^k
    static int smallestPeriod(String s) {
        int[] z = zArray(s);
        for (int p = 1; p < s.length(); p++) {
            if (p + z[p] == s.length()) return p;
        }
        return s.length();
    }

    public static void main(String[] args) {
        String s = "aabxaabxcaabxaabxay";
        int[] z = zArray(s);
        System.out.println("s = " + s);
        StringBuilder row = new StringBuilder("z = ");
        for (int v : z) row.append(v).append(' ');
        System.out.println(row.toString().strip());

        String text = "GET /api/users?id=1 HTTP/1.1 GET /api/users/1/roles HTTP/1.1 GET /api/user";
        System.out.println("\"/api/users\" at " + search("/api/users", text, '\0'));
        System.out.println("periods: abcabcab -> " + smallestPeriod("abcabcab")
                + ", aaaa -> " + smallestPeriod("aaaa") + ", abcd -> " + smallestPeriod("abcd"));

        // Self-check: Z-array against the O(n^2) definition, search against String.indexOf
        Random rnd = new Random(14);
        boolean ok = true;
        for (int t = 0; t < 2_000; t++) {
            String alphabet = t % 2 == 0 ? "ab" : "abc";
            String txt = random(rnd, alphabet, rnd.nextInt(200));
            String pat = random(rnd, alphabet, 1 + rnd.nextInt(5));
            ok &= Arrays.equals(zArray(txt), naiveZ(txt));
            ok &= search(pat, txt, '#').equals(indexOfAll(pat, txt));
        }
        System.out.println("2000 random strings match the naive Z-array and String.indexOf: " + ok);

        // Linear even on the worst case for naive matching: a^n
        String worst = "a".repeat(1_000_000);
        System.out.println("matches of a^1000 in a^1000000: " + search("a".repeat(1_000), worst, '#').size());
    }

    static String random(Random rnd, String alphabet, int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
        return sb.toString();
    }
}

/* Output:
s = aabxaabxcaabxaabxay
z = 0 1 0 0 4 1 0 0 0 8 1 0 0 5 1 0 0 1 0
"/api/users" at [4, 33]
periods: abcabcab -> 3, aaaa -> 1, abcd -> 4
2000 random strings match the naive Z-array and String.indexOf: true
matches of a^1000 in a^1000000: 999001
*/
