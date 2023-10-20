// A ReadWriteLock is a pair of locks for data that is read far more often
// than written (a permission cache, a routing table):
//  - the read lock is shared: any number of readers may hold it together
//  - the write lock is exclusive: no readers and no other writer
// ReentrantReadWriteLock also allows downgrading (take the read lock while
// holding the write lock, then release the write lock) but NOT upgrading:
// a reader that asks for the write lock waits for itself forever, so check
// with tryLock or release the read lock first.
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ReadWriteLockExample {
    static final class PermissionCache {
        final ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
        private final Map<String, Set<String>> grants = new HashMap<>();

        Set<String> permissionsOf(String user) {
            rw.readLock().lock();
            try {
                return grants.getOrDefault(user, Set.of());
            } finally {
                rw.readLock().unlock();
            }
        }

        void grant(String user, Set<String> perms) {
            rw.writeLock().lock();
            try {
                grants.put(user, Set.copyOf(perms));
            } finally {
                rw.writeLock().unlock();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        PermissionCache cache = new PermissionCache();
        cache.grant("alice", Set.of("read"));
        ReadWriteLock rw = cache.rw;

        // Three readers hold the read lock at the same time: each one counts down
        // and then waits for the others while still holding the lock. With an
        // exclusive lock this would time out.
        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch allInside = new CountDownLatch(3);
        CountDownLatch leave = new CountDownLatch(1);
        Future<?>[] readers = new Future<?>[3];
        for (int i = 0; i < 3; i++) {
            readers[i] = pool.submit(() -> {
                rw.readLock().lock();
                try {
                    allInside.countDown();
                    return allInside.await(2, TimeUnit.SECONDS) && leave.await(2, TimeUnit.SECONDS);
                } finally {
                    rw.readLock().unlock();
                }
            });
        }
        System.out.println("3 readers inside together: " + allInside.await(2, TimeUnit.SECONDS));
        System.out.println("read locks held: " + cache.rw.getReadLockCount());

        // A writer cannot get in while readers are inside
        System.out.println("writer tryLock while reading: " + rw.writeLock().tryLock());
        leave.countDown();
        for (Future<?> r : readers) r.get();
        boolean writing = rw.writeLock().tryLock(2, TimeUnit.SECONDS);
        System.out.println("writer tryLock after readers left: " + writing);
        if (writing) rw.writeLock().unlock();

        // Downgrade: write -> read is allowed
        rw.writeLock().lock();
        cache.grant("alice", Set.of("read", "write")); // reentrant write
        rw.readLock().lock();   // take the read lock before letting go of the write lock
        rw.writeLock().unlock();
        System.out.println("after downgrade, alice can: " + new java.util.TreeSet<>(cache.permissionsOf("alice"))
                + ", write locked: " + cache.rw.isWriteLocked());

        // Upgrade: read -> write is NOT allowed (lock() here would deadlock)
        System.out.println("upgrade attempt while holding read lock: " + rw.writeLock().tryLock());
        rw.readLock().unlock();

        pool.shutdown();
        System.out.println("terminated: " + pool.awaitTermination(2, TimeUnit.SECONDS));
    }
}

/* Output:
3 readers inside together: true
read locks held: 3
writer tryLock while reading: false
writer tryLock after readers left: true
after downgrade, alice can: [read, write], write locked: false
upgrade attempt while holding read lock: false
terminated: true
*/
