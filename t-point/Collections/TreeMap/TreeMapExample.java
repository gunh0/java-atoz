// TreeMap keeps its keys sorted (red-black tree, O(log n) operations) and is a
// NavigableMap: besides get/put it answers "which key is closest to x?"
// (floorKey, ceilingKey, lowerEntry, higherEntry) and hands out live range
// views (headMap, tailMap, subMap). That makes it the natural structure for
// lookup tables keyed by ranges: price tiers, time windows, IP ranges.
// Keys must be Comparable or a Comparator must be given; null keys are rejected.
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class TreeMapExample {
    public static void main(String[] args) {
        // Keys come out sorted regardless of insertion order
        Map<String, Integer> scores = new TreeMap<>();
        scores.put("mallory", 40);
        scores.put("alice", 90);
        scores.put("carol", 75);
        System.out.println("sorted by key: " + scores);

        // Range lookup: shipping fee by order weight in grams (lower bound -> fee)
        NavigableMap<Integer, String> fees = new TreeMap<>();
        fees.put(0, "3.00");
        fees.put(500, "5.00");
        fees.put(2000, "9.50");
        fees.put(10000, "25.00");
        for (int weight : new int[] {120, 500, 1999, 7300, 50000}) {
            Map.Entry<Integer, String> tier = fees.floorEntry(weight);
            System.out.println(weight + " g -> tier from " + tier.getKey() + " g, fee " + tier.getValue());
        }

        // Neighbours of a key
        System.out.println("lowerKey(500)   = " + fees.lowerKey(500));
        System.out.println("ceilingKey(501) = " + fees.ceilingKey(501));
        System.out.println("higherKey(10000) = " + fees.higherKey(10000));
        System.out.println("firstEntry() = " + fees.firstEntry() + ", lastKey() = " + fees.lastKey());

        // Range views
        System.out.println("headMap(2000)       = " + fees.headMap(2000));
        System.out.println("tailMap(2000)       = " + fees.tailMap(2000));
        System.out.println("subMap(500, 10000)  = " + fees.subMap(500, 10000));
        System.out.println("descendingMap()     = " + fees.descendingMap());

        // pollFirstEntry removes and returns: process events in time order
        NavigableMap<Integer, String> timeline = new TreeMap<>(Map.of(
                930, "standup", 1400, "review", 1100, "deploy"));
        while (!timeline.isEmpty()) {
            Map.Entry<Integer, String> next = timeline.pollFirstEntry();
            System.out.printf("%04d %s%n", next.getKey(), next.getValue());
        }
    }
}

/* Output:
sorted by key: {alice=90, carol=75, mallory=40}
120 g -> tier from 0 g, fee 3.00
500 g -> tier from 500 g, fee 5.00
1999 g -> tier from 500 g, fee 5.00
7300 g -> tier from 2000 g, fee 9.50
50000 g -> tier from 10000 g, fee 25.00
lowerKey(500)   = 0
ceilingKey(501) = 2000
higherKey(10000) = null
firstEntry() = 0=3.00, lastKey() = 10000
headMap(2000)       = {0=3.00, 500=5.00}
tailMap(2000)       = {2000=9.50, 10000=25.00}
subMap(500, 10000)  = {500=5.00, 2000=9.50}
descendingMap()     = {10000=25.00, 2000=9.50, 500=5.00, 0=3.00}
0930 standup
1100 deploy
1400 review
*/
