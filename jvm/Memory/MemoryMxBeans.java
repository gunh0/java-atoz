// Watching memory and GC from inside the JVM: Runtime and the management beans
//
// java.lang.management exposes what monitoring agents (JMX consoles,
// Prometheus exporters, APM tools) read: MemoryMXBean for heap and non-heap
// totals, one MemoryPoolMXBean per region (eden, survivor, old generation,
// Metaspace, code cache: the exact set depends on the collector the JVM
// chose), and one GarbageCollectorMXBean per collector with its collection
// count and accumulated time. Runtime offers the older trio maxMemory()
// (-Xmx), totalMemory() (committed) and freeMemory(). Every number is a
// snapshot that GC may change a microsecond later, and which collector runs
// depends on the machine (container CPU and memory limits switch HotSpot
// between Serial and G1), so this program prints only relationships that
// always hold, not values. A MemoryUsage always satisfies used <= committed
// <= max, where max may be undefined (-1).
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.List;

public class MemoryMxBeans {
    public static void main(String[] args) {
        Runtime rt = Runtime.getRuntime();
        System.out.println("Runtime: free <= total <= max: "
                + (rt.freeMemory() <= rt.totalMemory() && rt.totalMemory() <= rt.maxMemory()));
        System.out.println("Runtime: at least one processor: " + (rt.availableProcessors() >= 1));

        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        check("heap", memory.getHeapMemoryUsage());
        check("non-heap", memory.getNonHeapMemoryUsage());

        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        long heapPools = pools.stream().filter(p -> p.getType() == MemoryType.HEAP).count();
        System.out.println("heap pools present: " + (heapPools > 0)
                + ", Metaspace pool present: " + pools.stream().anyMatch(p -> p.getName().equals("Metaspace")));
        System.out.println("every pool belongs to a memory manager: "
                + pools.stream().allMatch(p -> p.getMemoryManagerNames().length > 0));

        // Collections counted per collector; System.gc() requests a full one
        List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();
        long before = gcs.stream().mapToLong(GarbageCollectorMXBean::getCollectionCount).sum();
        System.gc();
        long after = gcs.stream().mapToLong(GarbageCollectorMXBean::getCollectionCount).sum();
        System.out.println("collectors reported: " + (gcs.size() >= 1) + ", System.gc() counted: " + (after > before));

        // Each pool can also report usage right after the last GC
        boolean afterGcConsistent = pools.stream().filter(p -> p.getType() == MemoryType.HEAP)
                .map(MemoryPoolMXBean::getCollectionUsage).allMatch(u -> u == null || u.getUsed() <= u.getCommitted());
        System.out.println("heap pools after last GC: used <= committed: " + afterGcConsistent);
    }

    static void check(String label, MemoryUsage u) {
        boolean ok = u.getUsed() <= u.getCommitted() && (u.getMax() == -1 || u.getCommitted() <= u.getMax());
        System.out.println(label + ": used <= committed <= max (or max undefined): " + ok);
    }
}

/* Output:
Runtime: free <= total <= max: true
Runtime: at least one processor: true
heap: used <= committed <= max (or max undefined): true
non-heap: used <= committed <= max (or max undefined): true
heap pools present: true, Metaspace pool present: true
every pool belongs to a memory manager: true
collectors reported: true, System.gc() counted: true
heap pools after last GC: used <= committed: true
*/
