// A custom ClassLoader that defines classes from bytes compiled in memory
//
// A class loader's real job is findClass(name): locate the bytes of a class
// file somewhere (a JAR, a database, the network, a byte[]) and turn them
// into a Class with defineClass(). loadClass() wraps that with the parent-
// first delegation and caching, so a custom loader normally overrides
// findClass only. This is how application servers, plugin systems and
// scripting engines load code. Here the class file comes straight from the
// compiler: javax.tools.JavaCompiler compiles a source string, a small
// ForwardingJavaFileManager keeps the output in a map instead of writing
// .class files, and the loader defines classes from that map. Everything
// the loader defines is as trusted as the code that defined it, so a
// loader that takes bytes from outside is a code-execution feature.
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

public class InMemoryClassLoader {
    static final String SOURCE = """
            package plugin;
            public class Shout implements java.util.function.Function<String, String> {
                private static final String SUFFIX = new Helper().suffix();
                public String apply(String s) { return s.toUpperCase() + SUFFIX; }
            }
            class Helper { String suffix() { return "!"; } }
            """;

    // Defines classes from a name -> bytes map; everything else goes to the parent
    static final class BytesClassLoader extends ClassLoader {
        private final Map<String, byte[]> classes;
        final List<String> defined = new ArrayList<>();

        BytesClassLoader(Map<String, byte[]> classes, ClassLoader parent) {
            super("plugins", parent);
            this.classes = classes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] b = classes.get(name);
            if (b == null) throw new ClassNotFoundException(name);
            defined.add(name);
            return defineClass(name, b, 0, b.length);
        }
    }

    public static void main(String[] args) throws Exception {
        Map<String, byte[]> classes = compile("plugin.Shout", SOURCE);
        System.out.println("compiled classes: " + classes.keySet().stream().sorted().toList());

        BytesClassLoader loader = new BytesClassLoader(classes, ClassLoader.getPlatformClassLoader());
        Class<?> shout = loader.loadClass("plugin.Shout");
        System.out.println("loaded " + shout.getName() + " via loader \"" + shout.getClassLoader().getName() + "\"");

        // Function is a JDK interface: the bootstrap loader's class, shared by both sides
        @SuppressWarnings("unchecked")
        Function<String, String> f = (Function<String, String>) shout.getDeclaredConstructor().newInstance();
        System.out.println("apply(\"hello\") = " + f.apply("hello"));
        // Helper was loaded lazily, by the same loader, when Shout first needed it
        System.out.println("findClass calls, in order: " + loader.defined);

        // Second request: answered from the loader's cache, no new definition
        System.out.println("loadClass again returns the same Class: " + (loader.loadClass("plugin.Shout") == shout));
        System.out.println("findClass calls now: " + loader.defined.size());

        // The application loader knows nothing about these classes
        try {
            Class.forName("plugin.Shout");
        } catch (ClassNotFoundException e) {
            System.out.println("Class.forName from the program's loader: " + e.getClass().getSimpleName());
        }
        System.out.println("Class.forName with the plugin loader: " + Class.forName("plugin.Shout", true, loader).getSimpleName());
    }

    // Compiles one source file in memory and returns binary name -> class file bytes
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
        out.forEach((name, bytes) -> result.put(name, bytes.toByteArray()));
        return result;
    }
}

/* Output:
compiled classes: [plugin.Helper, plugin.Shout]
loaded plugin.Shout via loader "plugins"
apply("hello") = HELLO!
findClass calls, in order: [plugin.Shout, plugin.Helper]
loadClass again returns the same Class: true
findClass calls now: 2
Class.forName from the program's loader: ClassNotFoundException
Class.forName with the plugin loader: Shout
*/
