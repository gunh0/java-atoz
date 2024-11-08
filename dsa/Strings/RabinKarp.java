// Rabin-Karp compares hashes instead of characters: it hashes the pattern and
// every text window of the same length with a polynomial rolling hash, so moving
// the window one step costs O(1) (remove the leading char's term, shift, add the
// new char). Equal hashes are only candidates, since different strings can
// collide, so each candidate is verified character by character. Expected time
// O(n + m); its real strength is searching many patterns of one length at once
// (one hash-set lookup per window), shown below. A deliberately tiny modulus
// demonstrates why the verification step is not optional.
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

public class RabinKarp {
    static final long BASE = 256;
    static final long MOD = 1_000_000_007L;
    static int falsePositives;

    static long hash(String s, int from, int len, long mod) {
        long h = 0;
        for (int i = from; i < from + len; i++) {
            h = (h * BASE + s.charAt(i)) % mod;
        }
        return h;
    }

    static List<Integer> search(String text, String p, long mod) {
        List<Integer> hits = new ArrayList<>();
        int m = p.length(), n = text.length();
        if (m == 0 || m > n) {
            return hits;
        }
        long high = 1; // BASE^(m-1) % mod, the weight of the leading character
        for (int i = 1; i < m; i++) {
            high = high * BASE % mod;
        }
        long target = hash(p, 0, m, mod);
        long h = hash(text, 0, m, mod);
        for (int i = 0; ; i++) {
            if (h == target) {
                if (text.regionMatches(i, p, 0, m)) {
                    hits.add(i);
                } else {
                    falsePositives++;
                }
            }
            if (i + m >= n) {
                break;
            }
            // roll: drop text[i], append text[i + m]
            h = (h - text.charAt(i) * high % mod + mod) % mod;
            h = (h * BASE + text.charAt(i + m)) % mod;
        }
        return hits;
    }

    // Several patterns of the same length in one pass
    static Map<String, List<Integer>> searchAll(String text, List<String> patterns) {
        int m = patterns.get(0).length();
        Map<Long, List<String>> byHash = new HashMap<>();
        for (String p : patterns) {
            byHash.computeIfAbsent(hash(p, 0, m, MOD), k -> new ArrayList<>()).add(p);
        }
        Map<String, List<Integer>> result = new TreeMap<>();
        patterns.forEach(p -> result.put(p, new ArrayList<>()));
        for (int i = 0; i + m <= text.length(); i++) {
            // recomputed per window for brevity; search() shows the O(1) roll
            for (String p : byHash.getOrDefault(hash(text, i, m, MOD), List.of())) {
                if (text.regionMatches(i, p, 0, m)) {
                    result.get(p).add(i);
                }
            }
        }
        return result;
    }

    static List<Integer> indexOfAll(String text, String p) {
        List<Integer> hits = new ArrayList<>();
        for (int i = text.indexOf(p); i >= 0; i = text.indexOf(p, i + 1)) {
            hits.add(i);
        }
        return hits;
    }

    public static void main(String[] args) {
        String log = "GET /admin GET /login POST /login GET /admin/users POST /logout";
        System.out.println("'/admin' at " + search(log, "/admin", MOD));
        System.out.println("'POST' at " + search(log, "POST", MOD));

        falsePositives = 0;
        String text = "the quick brown fox jumps over the lazy dog then the fox sleeps";
        List<Integer> hits = search(text, "fox", 7); // absurdly small modulus
        System.out.println("mod 7:     hits " + hits + ", hash collisions rejected by verification: " + falsePositives);
        falsePositives = 0;
        hits = search(text, "fox", MOD);
        System.out.println("mod 1e9+7: hits " + hits + ", hash collisions rejected by verification: " + falsePositives);

        String dna = "ACGTACGTTAGCTAGCTACGATCGATCGTAGCTAG";
        System.out.println("multi-pattern: " + searchAll(dna, List.of("TAG", "CGA", "GGG", "ACG")));

        // Self-check against indexOf, with a large and a tiny modulus
        Random rnd = new Random(22);
        boolean ok = true;
        for (int t = 0; t < 2000; t++) {
            String s = random(rnd, rnd.nextInt(80));
            String q = random(rnd, 1 + rnd.nextInt(5));
            List<Integer> expected = indexOfAll(s, q);
            ok &= search(s, q, MOD).equals(expected) && search(s, q, 3).equals(expected);
        }
        System.out.println("2000 random searches match indexOf: " + ok);
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
'/admin' at [4, 38]
'POST' at [22, 51]
mod 7:     hits [16, 53], hash collisions rejected by verification: 9
mod 1e9+7: hits [16, 53], hash collisions rejected by verification: 0
multi-pattern: {ACG=[0, 4, 17], CGA=[18, 22], GGG=[], TAG=[8, 12, 28, 32]}
2000 random searches match indexOf: true
*/
