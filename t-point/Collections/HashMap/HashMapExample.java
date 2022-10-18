// HashMap maps keys to values in a hash table: O(1) put/get on average.
//  - keys follow the same hashCode()/equals() rules as HashSet elements
//  - one null key and any number of null values are allowed
//  - get() returns null both for "absent" and "mapped to null"; use
//    containsKey() or getOrDefault() to tell them apart
//  - since Java 8 the compound methods putIfAbsent, merge, compute* replace
//    most "check, then put" code
//  - iteration order is unspecified: sort (or use a TreeMap) before printing
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class HashMapExample {
    public static void main(String[] args) {
        Map<String, Integer> stock = new HashMap<>();
        stock.put("apple", 5);
        stock.put("pear", 2);
        Integer previous = stock.put("apple", 7); // put returns the old value
        System.out.println("put(apple, 7) replaced " + previous);
        System.out.println("get(apple) = " + stock.get("apple"));
        System.out.println("get(kiwi) = " + stock.get("kiwi"));
        System.out.println("getOrDefault(kiwi, 0) = " + stock.getOrDefault("kiwi", 0));
        System.out.println("containsKey(pear) = " + stock.containsKey("pear"));

        // putIfAbsent only writes when the key is missing
        stock.putIfAbsent("pear", 100);
        stock.putIfAbsent("plum", 1);
        System.out.println("after putIfAbsent: " + new TreeMap<>(stock));

        // merge: the idiomatic counter
        String log = "GET /a GET /b POST /a GET /a DELETE /b";
        Map<String, Integer> methodCounts = new HashMap<>();
        String[] tokens = log.split(" ");
        for (int i = 0; i < tokens.length; i += 2) {
            methodCounts.merge(tokens[i], 1, Integer::sum);
        }
        System.out.println("method counts: " + new TreeMap<>(methodCounts));

        // computeIfAbsent: the idiomatic multimap (group values by key)
        Map<String, List<String>> methodsByPath = new HashMap<>();
        for (int i = 0; i < tokens.length; i += 2) {
            methodsByPath.computeIfAbsent(tokens[i + 1], k -> new ArrayList<>()).add(tokens[i]);
        }
        System.out.println("methods by path: " + new TreeMap<>(methodsByPath));

        // computeIfPresent returning null removes the entry
        stock.computeIfPresent("plum", (k, v) -> v - 1 == 0 ? null : v - 1);
        System.out.println("plum sold out, still present: " + stock.containsKey("plum"));

        // Iterating: entrySet gives key and value without a second lookup
        int total = 0;
        for (Map.Entry<String, Integer> e : stock.entrySet()) {
            total += e.getValue();
        }
        System.out.println("total items: " + total);
        new TreeMap<>(stock).forEach((k, v) -> System.out.println("  " + k + " -> " + v));

        // null value vs absent key
        Map<String, String> config = new HashMap<>();
        config.put("proxy", null);
        System.out.println("get(proxy)=" + config.get("proxy") + ", containsKey(proxy)=" + config.containsKey("proxy"));
        System.out.println("get(port)=" + config.get("port") + ", containsKey(port)=" + config.containsKey("port"));
    }
}

/* Output:
put(apple, 7) replaced 5
get(apple) = 7
get(kiwi) = null
getOrDefault(kiwi, 0) = 0
containsKey(pear) = true
after putIfAbsent: {apple=7, pear=2, plum=1}
method counts: {DELETE=1, GET=3, POST=1}
methods by path: {/a=[GET, POST, GET], /b=[GET, DELETE]}
plum sold out, still present: false
total items: 9
  apple -> 7
  pear -> 2
get(proxy)=null, containsKey(proxy)=true
get(port)=null, containsKey(port)=false
*/
