// Class unloading: a class lives exactly as long as its class loader
//
// Classes are not collected one by one. A class can be unloaded only when
// its defining loader is unreachable, and a loader stays reachable as long
// as ANY of its classes is: through an instance (instance -> Class ->
// loader), a Class object stored somewhere, a static field of a parent-
// loaded class, a ThreadLocal value, a registered JDBC driver or shutdown
// hook. That is the anatomy of the classic "Metaspace/PermGen leak" on
// application-server redeploys: one forgotten reference pins the old
// web app's loader and every class it loaded. Classes of the bootstrap,
// platform and app loaders are never unloaded. (Hidden classes, see
// modern-java/java15, can be unloaded individually.) Unloading happens
// during GC, so the demo triggers System.gc() in a bounded loop and checks
// a WeakReference to the loader plus the ClassLoadingMXBean counter.
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

public class ClassUnloading {
    static final String SOURCE = """
            package plugin;
            public class Task implements java.util.function.Supplier<String> {
                static final byte[] CACHE = new byte[1 << 20]; // per-class static state
                public String get() { return "task ran"; }
            }
            """;

    static final class BytesClassLoader extends ClassLoader {
        private final Map<String, byte[]> classes;
        BytesClassLoader(Map<String, byte[]> classes) {
            super("plugin-loader", ClassLoader.getPlatformClassLoader());
            this.classes = classes;
        }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] b = classes.get(name);
            if (b == null) throw new ClassNotFoundException(name);
            return defineClass(name, b, 0, b.length);
        }
    }

    static Object leaked; // a "registry" in a long-lived class

    public static void main(String[] args) throws Exception {
        Map<String, byte[]> bytes = compile("plugin.Task", SOURCE);
        ClassLoadingMXBean mx = ManagementFactory.getClassLoadingMXBean();

        // 1. Use a plugin, then drop every reference to it
        long unloadedBefore = mx.getUnloadedClassCount();
        WeakReference<ClassLoader> loaderRef = runPlugin(bytes, false);
        System.out.println("plugin dropped, loader collected: " + gcUntil(() -> loaderRef.get() == null));
        System.out.println("JVM reports at least one class unloaded: " + (mx.getUnloadedClassCount() > unloadedBefore));

        // 2. Same plugin, but one instance is kept in a static field
        WeakReference<ClassLoader> pinnedRef = runPlugin(bytes, true);
        System.out.println("one instance kept, loader collected: " + gcUntil(() -> pinnedRef.get() == null));
        System.out.println("  path: leaked instance -> " + leaked.getClass().getName() + " -> " + leaked.getClass().getClassLoader().getName());
        leaked = null; // fix the leak
        System.out.println("after clearing the field, loader collected: " + gcUntil(() -> pinnedRef.get() == null));
    }

    static WeakReference<ClassLoader> runPlugin(Map<String, byte[]> bytes, boolean leak) throws Exception {
        ClassLoader loader = new BytesClassLoader(bytes);
        Supplier<?> task = (Supplier<?>) loader.loadClass("plugin.Task").getDeclaredConstructor().newInstance();
        task.get();
        if (leak) leaked = task;
        return new WeakReference<>(loader);
    }

    interface Condition { boolean holds(); }

    static boolean gcUntil(Condition c) throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            if (c.holds()) return true;
            System.gc();
            Thread.sleep(50);
        }
        return c.holds();
    }

    static Map<String, byte[]> compile(String className, String source) {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        Map<String, ByteArrayOutputStream> out = new HashMap<>();
        JavaFileManager fm = new ForwardingJavaFileManager<>(javac.getStandardFileManager(null, null, null)) {
            @Override
            public JavaFileObject getJavaFileForOutput(Location location, String name, JavaFileObject.Kind kind, FileObject sibling) {
                return new SimpleJavaFileObject(URI.create("mem:///" + name.replace('.', '/') + kind.extension), kind) {
                    @Override
                    public OutputStream openOutputStream() {
                        return out.computeIfAbsent(name, n -> new ByteArrayOutputStream());
                    }
                };
            }
        };
        JavaFileObject src = new SimpleJavaFileObject(
                URI.create("string:///" + className.replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) { return source; }
        };
        if (!javac.getTask(null, fm, null, List.of("--release", "17"), null, List.of(src)).call()) {
            throw new IllegalStateException("compilation failed");
        }
        Map<String, byte[]> result = new HashMap<>();
        out.forEach((name, b) -> result.put(name, b.toByteArray()));
        return result;
    }
}

/* Output:
plugin dropped, loader collected: true
JVM reports at least one class unloaded: true
one instance kept, loader collected: false
  path: leaked instance -> plugin.Task -> plugin-loader
after clearing the field, loader collected: true
*/
