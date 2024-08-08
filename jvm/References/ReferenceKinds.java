// Strong, soft, weak and phantom references, and the ReferenceQueue
//
// The garbage collector frees objects that are no longer reachable through
// ordinary (strong) references. java.lang.ref lets you point at an object
// without keeping it alive, at three strengths. A SoftReference is cleared
// only when memory runs short (HotSpot keeps recently used ones; all are
// cleared before an OutOfMemoryError), the classic but crude memory-sensitive
// cache. A WeakReference is cleared at the first GC that finds the object
// only weakly reachable (canonicalizing maps, listeners). A PhantomReference
// never returns its object (get() is always null); it is enqueued once the
// object is about to be reclaimed, the hook for post-mortem cleanup (what
// Cleaner uses). Registering a ReferenceQueue tells you WHEN a reference
// was cleared. GC timing is not deterministic, so the demo triggers
// System.gc() in a bounded loop and prints verdicts only.
import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

public class ReferenceKinds {
    public static void main(String[] args) throws Exception {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();

        Object strongTarget = new byte[1024];
        WeakReference<Object> weak = new WeakReference<>(strongTarget, queue);
        System.out.println("weak.get() while strongly reachable: " + (weak.get() == strongTarget));

        // Drop the strong reference; the next GCs may clear the weak one
        strongTarget = null;
        System.out.println("weak cleared after GC: " + gcUntil(() -> weak.get() == null));
        Reference<?> polled = queue.remove(5_000);
        System.out.println("weak reference enqueued: " + (polled == weak));

        // Phantom: get() is always null, even while the object is alive
        Object phantomTarget = new byte[1024];
        PhantomReference<Object> phantom = new PhantomReference<>(phantomTarget, queue);
        System.out.println("phantom.get() while alive: " + phantom.get());
        phantomTarget = null;
        Reference<?>[] fromQueue = new Reference<?>[1];
        System.out.println("phantom enqueued after GC: " + gcUntil(() -> (fromQueue[0] = queue.poll()) != null));
        System.out.println("queue hands back that reference: " + (fromQueue[0] == phantom));

        // Soft: kept across an ordinary GC when memory is plentiful
        SoftReference<Object> soft = new SoftReference<>(new byte[1024]);
        soft.get(); // recently used soft references are the last to go
        System.gc();
        System.out.println("soft reference survives a GC without memory pressure: " + (soft.get() != null));

        // A reachability fence keeps an object alive up to a point in the code,
        // even if the JIT sees no further use of the local variable
        Object fenced = new Object();
        WeakReference<Object> fencedRef = new WeakReference<>(fenced);
        System.gc();
        System.out.println("object kept alive by a later reachabilityFence: " + (fencedRef.get() != null));
        Reference.reachabilityFence(fenced);
    }

    interface Condition { boolean holds(); }

    // System.gc() is only a request: retry a bounded number of times
    static boolean gcUntil(Condition c) throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            if (c.holds()) return true;
            System.gc();
            Thread.sleep(20);
        }
        return c.holds();
    }
}

/* Output:
weak.get() while strongly reachable: true
weak cleared after GC: true
weak reference enqueued: true
phantom.get() while alive: null
phantom enqueued after GC: true
queue hands back that reference: true
soft reference survives a GC without memory pressure: true
object kept alive by a later reachabilityFence: true
*/
