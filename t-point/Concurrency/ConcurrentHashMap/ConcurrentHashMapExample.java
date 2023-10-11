// ConcurrentHashMap is the thread-safe Map for shared state. Individual
// get/put are safe, but "check, then act" sequences are not:
//     if (!map.containsKey(k)) map.put(k, v);   // two threads can both put
//     map.put(k, map.get(k) + 1);               // increments get lost
// Use the atomic compound methods instead: putIfAbsent, computeIfAbsent,
// merge, compute. They run the update for one key atomically.
// Other differences from HashMap: no null keys or values, iterators never
// throw ConcurrentModificationException (they are weakly consistent), and
// bulk operations (forEach/search/reduce) take a parallelism threshold.
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

public class ConcurrentHashMapExample {
    public static void main(String[] args) throws InterruptedException {
        // Failed logins from an auth log, processed by 4 threads at once
        List<String> failedFrom = List.of("10.0.0.5", "10.0.0.7", "10.0.0.5", "10.0.0.9", "10.0.0.5", "10.0.0.7");
        ConcurrentHashMap<String, Integer> failures = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, LongAdder> bytesByIp = new ConcurrentHashMap<>();
        Set<String> seen = ConcurrentHashMap.newKeySet(); // a concurrent Set

        ExecutorService pool = Executors.newFixedThreadPool(4);
        for (int copy = 0; copy < 1000; copy++) { // replay the log 1000 times
            pool.execute(() -> {
                for (String ip : failedFrom) {
                    failures.merge(ip, 1, Integer::sum);                         // atomic counter
                    bytesByIp.computeIfAbsent(ip, k -> new LongAdder()).add(512); // atomic init
                    seen.add(ip);
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("failures: " + new TreeMap<>(failures));
        Map<String, Long> bytes = new TreeMap<>();
        bytesByIp.forEach((ip, adder) -> bytes.put(ip, adder.sum()));
        System.out.println("bytes:    " + bytes);
        System.out.println("distinct IPs: " + seen.size());

        // compute: read-modify-write of one entry, here a lockout decision
        failures.compute("10.0.0.5", (ip, n) -> n >= 3000 ? -1 : n); // -1 marks "locked out"
        System.out.println("10.0.0.5 after compute: " + failures.get("10.0.0.5"));

        // Bulk operations; threshold Long.MAX_VALUE = run sequentially
        int total = failures.reduceValues(Long.MAX_VALUE, n -> Math.max(n, 0), Integer::sum);
        String ipWith2000 = failures.search(Long.MAX_VALUE, (ip, n) -> n == 2000 ? ip : null);
        System.out.println("total non-locked failures: " + total + ", ip with 2000: " + ipWith2000);

        // Weakly consistent iteration: modifying while iterating is allowed
        for (String ip : failures.keySet()) {
            if (failures.get(ip) < 0) failures.remove(ip);
        }
        System.out.println("after removing locked-out IPs: " + new TreeMap<>(failures));

        // Nulls are rejected, so get() == null always means "absent"
        try {
            failures.put("10.0.0.1", null);
        } catch (NullPointerException e) {
            System.out.println("put(key, null) -> NullPointerException");
        }
    }
}

/* Output:
failures: {10.0.0.5=3000, 10.0.0.7=2000, 10.0.0.9=1000}
bytes:    {10.0.0.5=1536000, 10.0.0.7=1024000, 10.0.0.9=512000}
distinct IPs: 3
10.0.0.5 after compute: -1
total non-locked failures: 3000, ip with 2000: 10.0.0.7
after removing locked-out IPs: {10.0.0.7=2000, 10.0.0.9=1000}
put(key, null) -> NullPointerException
*/
