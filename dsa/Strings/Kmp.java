// Knuth-Morris-Pratt string search finds every occurrence of a pattern in a text
// in O(n + m), never moving backwards in the text. The precomputed failure
// function (prefix function) says, for each prefix of the pattern, how long its
// longest proper prefix that is also a suffix is; after a mismatch the search
// falls back to that length instead of restarting, because those characters are
// already known to match. The naive search is O(n * m) in the worst case, e.g.
// pattern "aaaab" in a text of a's, which the comparison counters below show.
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class Kmp {
    static long comparisons;

    // fail[i] = length of the longest proper border of pattern[0..i]
    static int[] failure(String p) {
        int[] fail = new int[p.length()];
        int k = 0;
        for (int i = 1; i < p.length(); i++) {
            while (k > 0 && p.charAt(i) != p.charAt(k)) {
                k = fail[k - 1];
            }
            if (p.charAt(i) == p.charAt(k)) {
                k++;
            }
            fail[i] = k;
        }
        return fail;
    }

    static List<Integer> search(String text, String p) {
        List<Integer> hits = new ArrayList<>();
        if (p.isEmpty()) {
            return hits;
        }
        int[] fail = failure(p);
        int k = 0; // number of pattern characters currently matched
        for (int i = 0; i < text.length(); i++) {
            while (k > 0 && text.charAt(i) != p.charAt(k)) {
                comparisons++;
                k = fail[k - 1];
            }
            comparisons++;
            if (text.charAt(i) == p.charAt(k)) {
                k++;
            }
            if (k == p.length()) {
                hits.add(i - k + 1);
                k = fail[k - 1]; // allow overlapping matches
            }
        }
        return hits;
    }

    static List<Integer> naive(String text, String p) {
        List<Integer> hits = new ArrayList<>();
        for (int i = 0; i + p.length() <= text.length(); i++) {
            int j = 0;
            while (j < p.length()) {
                comparisons++;
                if (text.charAt(i + j) != p.charAt(j)) {
                    break;
                }
                j++;
            }
            if (j == p.length() && !p.isEmpty()) {
                hits.add(i);
            }
        }
        return hits;
    }

    // Reference: repeated String.indexOf, stepping one char to allow overlaps
    static List<Integer> indexOfAll(String text, String p) {
        List<Integer> hits = new ArrayList<>();
        for (int i = text.indexOf(p); i >= 0 && !p.isEmpty(); i = text.indexOf(p, i + 1)) {
            hits.add(i);
        }
        return hits;
    }

    public static void main(String[] args) {
        String p = "ABABCABAB";
        System.out.println("failure(" + p + ") = " + Arrays.toString(failure(p)));
        String text = "ABABDABABCABABCABAB";
        System.out.println("matches in " + text + ": " + search(text, p));
        System.out.println("overlapping 'aa' in 'aaaa': " + search("aaaa", "aa"));

        String worst = "a".repeat(10_000);
        String pat = "aaaab";
        comparisons = 0;
        List<Integer> k = search(worst, pat);
        long kmpCount = comparisons;
        comparisons = 0;
        List<Integer> n = naive(worst, pat);
        System.out.println("worst case, text of 10000 a's, pattern aaaab: kmp " + kmpCount
                + " comparisons, naive " + comparisons + ", both found " + k.size() + "/" + n.size());

        // Self-check against indexOf on random strings over a tiny alphabet
        Random rnd = new Random(21);
        boolean ok = true;
        for (int t = 0; t < 2000; t++) {
            String s = randomString(rnd, rnd.nextInt(60));
            String q = randomString(rnd, 1 + rnd.nextInt(4));
            ok &= search(s, q).equals(indexOfAll(s, q));
        }
        System.out.println("2000 random searches match indexOf: " + ok);
    }

    static String randomString(Random rnd, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append((char) ('a' + rnd.nextInt(2)));
        }
        return sb.toString();
    }
}

/* Output:
failure(ABABCABAB) = [0, 0, 1, 2, 0, 1, 2, 3, 4]
matches in ABABDABABCABABCABAB: [5, 10]
overlapping 'aa' in 'aaaa': [0, 1, 2]
worst case, text of 10000 a's, pattern aaaab: kmp 19996 comparisons, naive 49980, both found 0/0
2000 random searches match indexOf: true
*/
