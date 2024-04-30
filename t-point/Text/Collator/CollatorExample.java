// String.compareTo compares UTF-16 code units, so "Zebra" sorts before
// "apple" and accented letters land after 'z'. A java.text.Collator compares
// strings the way people of a locale expect.
// Strength decides which differences count:
//   PRIMARY    base letters only        (a = A = \u00E1)
//   SECONDARY  plus accents             (a = A, a != \u00E1)
//   TERTIARY   plus case (the default)  (a != A != \u00E1)
// Collators are Comparators, so they plug into sort and TreeMap. When the same
// strings are compared many times, precompute CollationKeys once and compare
// those instead.
import java.text.CollationKey;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

public class CollatorExample {
    // Escape non-ASCII characters so the output does not depend on the console encoding
    static String ascii(Object o) {
        StringBuilder sb = new StringBuilder();
        o.toString().chars().forEach(c -> sb.append(c < 128 ? String.valueOf((char) c) : String.format("\\u%04x", c)));
        return sb.toString();
    }

    public static void main(String[] args) {
        List<String> words = List.of("peach", "Zebra", "apple", "\u00E9clair", "Apple", "zebra", "eclair", "banana");

        List<String> byCodeUnit = new ArrayList<>(words);
        byCodeUnit.sort(null);
        System.out.println("compareTo:    " + ascii(byCodeUnit));

        Collator us = Collator.getInstance(Locale.US);
        List<String> byCollator = new ArrayList<>(words);
        byCollator.sort(us);
        System.out.println("Collator(US): " + ascii(byCollator));

        // Strength
        String[][] pairs = {{"a", "A"}, {"a", "\u00E1"}, {"resume", "R\u00E9sum\u00E9"}};
        for (int strength : new int[] {Collator.PRIMARY, Collator.SECONDARY, Collator.TERTIARY}) {
            Collator c = Collator.getInstance(Locale.US);
            c.setStrength(strength);
            StringBuilder sb = new StringBuilder(String.format("%-9s", name(strength)));
            for (String[] p : pairs) {
                sb.append(" ").append(ascii(p[0])).append(c.equals(p[0], p[1]) ? " == " : " != ").append(ascii(p[1])).append(";");
            }
            System.out.println(sb);
        }

        // A case- and accent-insensitive set: duplicates by PRIMARY strength collapse
        Collator primary = Collator.getInstance(Locale.US);
        primary.setStrength(Collator.PRIMARY);
        TreeSet<String> tags = new TreeSet<>(primary);
        tags.addAll(List.of("Cafe", "caf\u00E9", "CAFE", "tea", "Tea"));
        System.out.println("primary set:  " + ascii(tags));

        // CollationKeys: compute once, compare cheaply
        CollationKey[] keys = words.stream().map(us::getCollationKey).toArray(CollationKey[]::new);
        Arrays.sort(keys);
        List<String> fromKeys = new ArrayList<>();
        for (CollationKey k : keys) {
            fromKeys.add(k.getSourceString());
        }
        System.out.println("by keys:      " + ascii(fromKeys));
        System.out.println("same order as Collator: " + fromKeys.equals(byCollator));
    }

    static String name(int strength) {
        return switch (strength) {
            case Collator.PRIMARY -> "PRIMARY";
            case Collator.SECONDARY -> "SECONDARY";
            default -> "TERTIARY";
        };
    }
}

/* Output:
compareTo:    [Apple, Zebra, apple, banana, eclair, peach, zebra, \u00e9clair]
Collator(US): [apple, Apple, banana, eclair, \u00e9clair, peach, zebra, Zebra]
PRIMARY   a == A; a == \u00e1; resume == R\u00e9sum\u00e9;
SECONDARY a == A; a != \u00e1; resume != R\u00e9sum\u00e9;
TERTIARY  a != A; a != \u00e1; resume != R\u00e9sum\u00e9;
primary set:  [Cafe, tea]
by keys:      [apple, Apple, banana, eclair, \u00e9clair, peach, zebra, Zebra]
same order as Collator: true
*/
