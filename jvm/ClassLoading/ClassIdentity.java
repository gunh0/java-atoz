// Class identity: a class is its name PLUS its defining loader
//
// At run time the JVM identifies a class by the pair (binary name, defining
// class loader). Define the same bytes in two loaders and you get two
// unrelated classes with the same name: separate static fields, instanceof
// false, and the famous "ClassCastException: com.x.Foo cannot be cast to
// com.x.Foo" of application servers and plugin systems, where a class
// reached the caller through a different loader than the one the caller
// was linked against. The cure is to share the types that cross the
// boundary through a COMMON parent loader (here the JDK's own Supplier
// interface), and only load implementations per plugin. Isolation by loader
// is also what lets two versions of a library coexist in one JVM.
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
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

public class ClassIdentity {
    static final String SOURCE = """
            package lib;
            public class Counter implements java.util.function.Supplier<Integer> {
                static int created;                     // one per (class, loader)
                public Counter() { created++; }
                public Integer get() { return created; }
            }
            """;

    static final class BytesClassLoader extends ClassLoader {
        private final Map<String, byte[]> classes;

        BytesClassLoader(String name, Map<String, byte[]> classes) {
            super(name, ClassLoader.getPlatformClassLoader());
            this.classes = classes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] b = classes.get(name);
            if (b == null) throw new ClassNotFoundException(name);
            return defineClass(name, b, 0, b.length);
        }
    }

    public static void main(String[] args) throws Exception {
        Map<String, byte[]> bytes = compile("lib.Counter", SOURCE);
        Class<?> a = new BytesClassLoader("loader-A", bytes).loadClass("lib.Counter");
        Class<?> b = new BytesClassLoader("loader-B", bytes).loadClass("lib.Counter");

        System.out.println("same name: " + a.getName().equals(b.getName()));
        System.out.println("same Class object: " + (a == b));
        System.out.println("defined by: " + a.getClassLoader().getName() + " / " + b.getClassLoader().getName());

        Object fromA = a.getDeclaredConstructor().newInstance();
        Object fromB1 = b.getDeclaredConstructor().newInstance();
        Object fromB2 = b.getDeclaredConstructor().newInstance();
        System.out.println("A instance instanceof B's Counter: " + b.isInstance(fromA));
        try {
            b.cast(fromA);
        } catch (ClassCastException e) {
            System.out.println("casting A's Counter to B's Counter: " + e.getClass().getSimpleName());
        }

        // Static state is per class, so per loader
        System.out.println("A's Counter.created = " + ((Supplier<?>) fromA).get()
                + ", B's Counter.created = " + ((Supplier<?>) fromB2).get());

        // The shared interface comes from the bootstrap loader: one Supplier for everyone
        Class<?> ifaceA = a.getInterfaces()[0], ifaceB = b.getInterfaces()[0];
        System.out.println("both implement the same Supplier: " + (ifaceA == ifaceB && ifaceA == Supplier.class));
        System.out.println("both usable through it: " + (fromA instanceof Supplier<?> && fromB1 instanceof Supplier<?>));
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
same name: true
same Class object: false
defined by: loader-A / loader-B
A instance instanceof B's Counter: false
casting A's Counter to B's Counter: ClassCastException
A's Counter.created = 1, B's Counter.created = 2
both implement the same Supplier: true
both usable through it: true
*/
