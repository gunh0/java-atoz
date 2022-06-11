// Singleton (creational): ensure a class has exactly one instance and give
// everyone a single, well-known way to reach it.
//
// The interesting part is not "one instance" but WHEN it is created and
// whether creation is safe when several threads ask at once. This file
// compares five common variants for an application-wide configuration
// registry. (Effective Java Item 3, in effective/03_Singleton.java, covers
// the other angle: reflection and serialization attacks, where enum wins.)
// JDK example: Runtime.getRuntime() is an eagerly created singleton.
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class SingletonDemo {
    // Records the order in which the singletons are really constructed
    static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());

    // 1. Eager: created when the class is initialized. Simple and thread-safe
    //    (the JVM serializes class initialization), but never lazy.
    static final class EagerConfig {
        private static final EagerConfig INSTANCE = new EagerConfig();
        private EagerConfig() { LOG.add("EagerConfig created"); }
        static EagerConfig getInstance() { return INSTANCE; }
        static String source() { return "eager.properties"; }
    }

    // 2. Lazy + synchronized: correct, but every call pays for the lock,
    //    even long after the instance exists.
    static final class SynchronizedConfig {
        private static SynchronizedConfig instance;
        private SynchronizedConfig() { LOG.add("SynchronizedConfig created"); }
        static synchronized SynchronizedConfig getInstance() {
            if (instance == null) {
                instance = new SynchronizedConfig();
            }
            return instance;
        }
    }

    // 3. Double-checked locking: lock only while the instance is missing.
    //    The field MUST be volatile, otherwise another thread may see a
    //    non-null reference to a not yet fully constructed object.
    static final class DoubleCheckedConfig {
        private static volatile DoubleCheckedConfig instance;
        private DoubleCheckedConfig() { LOG.add("DoubleCheckedConfig created"); }
        static DoubleCheckedConfig getInstance() {
            DoubleCheckedConfig local = instance; // one volatile read on the fast path
            if (local == null) {
                synchronized (DoubleCheckedConfig.class) {
                    local = instance;
                    if (local == null) {
                        instance = local = new DoubleCheckedConfig();
                    }
                }
            }
            return local;
        }
    }

    // 4. Initialization-on-demand holder: the nested class is initialized on
    //    first use of getInstance(), and the JVM makes that thread-safe. Lazy
    //    and lock-free on the fast path, with no volatile subtleties.
    static final class HolderConfig {
        private HolderConfig() { LOG.add("HolderConfig created"); }
        private static final class Holder {
            static final HolderConfig INSTANCE = new HolderConfig();
        }
        static HolderConfig getInstance() { return Holder.INSTANCE; }
        static String source() { return "holder.properties"; }
    }

    // 5. Enum: one constant, created when the enum is initialized.
    //    Also immune to reflection and serialization duplicates.
    enum EnumConfig {
        INSTANCE;
        EnumConfig() { LOG.add("EnumConfig created"); }
        private final Map<String, String> settings = Map.of("region", "ap-northeast-2");
        String get(String key) { return settings.get(key); }
    }

    public static void main(String[] args) throws Exception {
        LOG.add("main started, nothing created yet");

        // "Eager" means "when the class is initialized", i.e. on its first
        // active use. Calling an unrelated static method is enough to build
        // EagerConfig, but not HolderConfig, whose instance lives in Holder.
        LOG.add("reading " + EagerConfig.source());
        LOG.add("reading " + HolderConfig.source());

        System.out.println("eager same instance: " + check(EagerConfig::getInstance));
        System.out.println("synchronized same instance: " + check(SynchronizedConfig::getInstance));
        System.out.println("double-checked same instance: " + check(DoubleCheckedConfig::getInstance));
        System.out.println("holder same instance: " + check(HolderConfig::getInstance));
        System.out.println("enum same instance: " + check(() -> EnumConfig.INSTANCE));
        System.out.println("enum region = " + EnumConfig.INSTANCE.get("region"));

        System.out.println("construction log:");
        LOG.forEach(line -> System.out.println("  " + line));

        // The JDK's own eager singleton
        System.out.println("Runtime.getRuntime() is a singleton: "
                + (Runtime.getRuntime() == Runtime.getRuntime()));
    }

    // 16 threads ask for the instance at the same moment; a correct singleton
    // hands every one of them the same object
    private static <T> boolean check(Supplier<T> getter) throws InterruptedException {
        Set<Object> seen = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch done = new CountDownLatch(16);
        for (int i = 0; i < 16; i++) {
            pool.execute(() -> {
                try {
                    start.await();
                    seen.add(getter.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();
        return seen.size() == 1;
    }
}

/* Output:
eager same instance: true
synchronized same instance: true
double-checked same instance: true
holder same instance: true
enum same instance: true
enum region = ap-northeast-2
construction log:
  main started, nothing created yet
  EagerConfig created
  reading eager.properties
  reading holder.properties
  SynchronizedConfig created
  DoubleCheckedConfig created
  HolderConfig created
  EnumConfig created
Runtime.getRuntime() is a singleton: true
*/
