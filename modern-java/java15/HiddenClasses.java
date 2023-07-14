// JEP 371 — Hidden Classes (Java 15)
//
// Frameworks generate classes at run time: lambda forms, dynamic proxies,
// scripting-language runtimes, serializer and mock libraries. They used to
// define them with ClassLoader.defineClass (the class then has a real,
// discoverable name, lives as long as its loader, and often needs a fresh
// loader per class to be unloadable) or with the internal
// Unsafe.defineAnonymousClass. Hidden classes are the supported
// replacement: Lookup.defineHiddenClass(bytes) creates a class that cannot
// be found by name or linked against by other classes, is only reachable
// through the returned Class object, and can be unloaded as soon as it is
// unreachable, independently of its loader. Here the bytes come from
// compiling a small source string in memory with javax.tools.
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.List;
import java.util.function.Supplier;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

public class HiddenClasses {
    // The generated code: an ordinary class in the unnamed package (the lookup's package)
    static final String SOURCE = "public class Greeter implements java.util.function.Supplier<String> {\n"
            + "    public String get() { return \"hello from \" + (getClass().isHidden() ? \"a hidden\" : \"a normal\") + \" class\"; }\n"
            + "}\n";

    public static void main(String[] args) throws Throwable {
        byte[] bytes = compile("Greeter", SOURCE);

        // Before: define it through a class loader -> a normal, named, findable class
        DefiningLoader definer = new DefiningLoader();
        Class<?> normal = definer.define("Greeter", bytes);
        System.out.println("before: name " + normal.getName() + ", found by name: "
                + (Class.forName("Greeter", false, definer) == normal));
        System.out.println("before: " + ((Supplier<?>) normal.getDeclaredConstructor().newInstance()).get());

        // After: a hidden class, defined relative to a Lookup on this class
        MethodHandles.Lookup lookup = MethodHandles.lookup()
                .defineHiddenClass(bytes, true); // true = initialize the class now
        Class<?> hidden = lookup.lookupClass();
        Supplier<?> greeter = (Supplier<?>) hidden.getDeclaredConstructor().newInstance();
        System.out.println("after: " + greeter.get());
        System.out.println("after: isHidden = " + hidden.isHidden()
                + ", name starts with \"Greeter/\" = " + hidden.getName().startsWith("Greeter/"));

        // Its name is not a binary name: no loader can find or link to it
        try {
            Class.forName(hidden.getName(), false, hidden.getClassLoader());
        } catch (ClassNotFoundException e) {
            System.out.println("after: Class.forName fails with ClassNotFoundException");
        }

        // Every definition is a distinct class, even from the same bytes
        Class<?> another = MethodHandles.lookup().defineHiddenClass(bytes, false).lookupClass();
        System.out.println("after: second definition is a different class = " + (another != hidden)
                + ", same loader as host = " + (another.getClassLoader() == HiddenClasses.class.getClassLoader()));
    }

    static class DefiningLoader extends ClassLoader {
        DefiningLoader() {
            super(HiddenClasses.class.getClassLoader());
        }

        Class<?> define(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }

    // Compile one source file to bytes without touching the file system
    static byte[] compile(String className, String source) {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        ByteArrayOutputStream classBytes = new ByteArrayOutputStream();
        JavaFileObject input = new SimpleJavaFileObject(URI.create("string:///" + className + ".java"),
                JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        JavaFileManager files = new ForwardingJavaFileManager<>(javac.getStandardFileManager(null, null, null)) {
            @Override
            public JavaFileObject getJavaFileForOutput(Location location, String name, JavaFileObject.Kind kind,
                                                       FileObject sibling) {
                return new SimpleJavaFileObject(URI.create("bytes:///" + name + ".class"), kind) {
                    @Override
                    public OutputStream openOutputStream() {
                        return classBytes;
                    }
                };
            }
        };
        boolean ok = javac.getTask(null, files, null, List.of("--release", "15"), null, List.of(input)).call();
        if (!ok) {
            throw new IllegalStateException("compilation failed");
        }
        return classBytes.toByteArray();
    }
}

/* Output:
before: name Greeter, found by name: true
before: hello from a normal class
after: hello from a hidden class
after: isHidden = true, name starts with "Greeter/" = true
after: Class.forName fails with ClassNotFoundException
after: second definition is a different class = true, same loader as host = true
*/
