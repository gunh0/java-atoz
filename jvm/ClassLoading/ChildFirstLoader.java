// Parent-first vs child-first delegation, and why java.* cannot be replaced
//
// The default ClassLoader.loadClass asks the parent first, so a class that
// exists higher up always wins. Web containers (servlet spec), OSGi and
// plugin hosts often invert this for application classes ("child-first")
// so that a web app can bundle its own version of a library that the
// server also has. Child-first is powerful and dangerous: it can shadow
// classes the host relies on, so containers always keep java.*, javax.* and
// their own API packages parent-first. The JVM adds a hard rule on top:
// only the bootstrap/platform loaders may define classes in java.* packages;
// defineClass throws SecurityException for anyone else, so no loader can
// smuggle in its own java.lang.String. Two versions of lib.Greeter below:
// v1 known to the parent loader, v2 to the child.
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

public class ChildFirstLoader {
    static String greeter(String version) {
        return """
                package lib;
                public class Greeter implements java.util.function.Supplier<String> {
                    public String get() { return "Greeter %s"; }
                }
                """.formatted(version);
    }

    // Parent-first (the default loadClass), defining from a byte map
    static class BytesClassLoader extends ClassLoader {
        final Map<String, byte[]> classes;

        BytesClassLoader(String name, ClassLoader parent, Map<String, byte[]> classes) {
            super(name, parent);
            this.classes = classes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] b = classes.get(name);
            if (b == null) throw new ClassNotFoundException(name);
            return defineClass(name, b, 0, b.length);
        }
    }

    // Child-first for everything except the platform's own packages
    static class ChildFirst extends BytesClassLoader {
        ChildFirst(String name, ClassLoader parent, Map<String, byte[]> classes) {
            super(name, parent, classes);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null && !name.startsWith("java.") && !name.startsWith("javax.")) {
                    try {
                        c = findClass(name); // look locally first
                    } catch (ClassNotFoundException ignored) {
                        // fall through to the parent
                    }
                }
                if (c == null) c = super.loadClass(name, false);
                if (resolve) resolveClass(c);
                return c;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader host = new BytesClassLoader("host", ClassLoader.getPlatformClassLoader(),
                compile("lib.Greeter", greeter("v1 (host)")));
        Map<String, byte[]> v2 = compile("lib.Greeter", greeter("v2 (bundled)"));

        ClassLoader parentFirst = new BytesClassLoader("app-parent-first", host, v2);
        ClassLoader childFirst = new ChildFirst("app-child-first", host, v2);
        System.out.println("parent-first app gets: " + greet(parentFirst) + " from loader " + loaderOf(parentFirst));
        System.out.println("child-first app gets:  " + greet(childFirst) + " from loader " + loaderOf(childFirst));

        // Platform classes still come from the top, even for the child-first loader
        System.out.println("child-first loads java.lang.String from bootstrap: "
                + (childFirst.loadClass("java.lang.String").getClassLoader() == null));

        // And nobody but the JDK may define classes in java.*: the name is
        // rejected before the bytes are even parsed (these are Greeter's)
        Map<String, byte[]> fake = Map.of("java.lang.Evil", v2.get("lib.Greeter"));
        try {
            new BytesClassLoader("rogue", null, fake).loadClass("java.lang.Evil");
        } catch (SecurityException e) {
            System.out.println("defining java.lang.Evil: " + e.getClass().getSimpleName());
        }
    }

    static String greet(ClassLoader l) throws Exception {
        return ((Supplier<?>) l.loadClass("lib.Greeter").getDeclaredConstructor().newInstance()).get().toString();
    }

    static String loaderOf(ClassLoader l) throws Exception {
        return l.loadClass("lib.Greeter").getClassLoader().getName();
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
parent-first app gets: Greeter v1 (host) from loader host
child-first app gets:  Greeter v2 (bundled) from loader app-child-first
child-first loads java.lang.String from bootstrap: true
defining java.lang.Evil: SecurityException
*/
