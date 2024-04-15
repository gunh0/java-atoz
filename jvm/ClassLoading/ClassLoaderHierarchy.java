// The class loader hierarchy and parent-first delegation
//
// Every class is loaded by a class loader, and the JDK sets up three:
// the bootstrap loader (native, shown as null) loads the core modules such
// as java.base; the platform loader loads the other Java SE / JDK modules
// (java.sql, java.xml.crypto, ...); the application ("system") loader loads
// your class path and module path. A loader first asks its PARENT, and only
// if the parent cannot find the class does it look itself. That is what
// keeps a "java.lang.String" on the class path from replacing the real one,
// and why core classes are shared by everything in the JVM. Running a file
// with `java File.java` adds one more layer: the source launcher compiles
// the file in memory and loads it with its own loader, a child of the
// application loader.
import java.net.URL;

public class ClassLoaderHierarchy {
    public static void main(String[] args) throws Exception {
        ClassLoader app = ClassLoader.getSystemClassLoader();
        ClassLoader platform = ClassLoader.getPlatformClassLoader();

        show("java.lang.String", String.class);
        show("java.sql.Date", java.sql.Date.class);
        show("java.net.http.HttpClient", java.net.http.HttpClient.class);
        show("this program", ClassLoaderHierarchy.class);

        // The chain upwards from this program's loader
        System.out.print("\nparent chain: ");
        for (ClassLoader l = ClassLoaderHierarchy.class.getClassLoader(); ; l = l.getParent()) {
            System.out.print(name(l));
            if (l == null) break;
            System.out.print(" -> ");
        }
        System.out.println();
        System.out.println("app's parent is platform: " + (app.getParent() == platform));
        System.out.println("platform's parent is bootstrap (null): " + (platform.getParent() == null));

        // Delegation: asking the application loader for String returns the
        // bootstrap's class, the one and only java.lang.String
        Class<?> viaApp = app.loadClass("java.lang.String");
        System.out.println("\napp.loadClass(\"java.lang.String\") == String.class: " + (viaApp == String.class));
        System.out.println("its defining loader: " + name(viaApp.getClassLoader()));

        // Resources are found the same way; core classes live in the jrt: image
        URL res = app.getResource("java/lang/String.class");
        System.out.println("String.class resource protocol: " + res.getProtocol());

        // A class nobody can find
        try {
            app.loadClass("com.example.DoesNotExist");
        } catch (ClassNotFoundException e) {
            System.out.println("unknown class: " + e.getClass().getSimpleName());
        }
    }

    static void show(String label, Class<?> c) {
        System.out.printf("%-26s module %-20s loader %s%n", label,
                c.getModule().isNamed() ? c.getModule().getName() : "(unnamed)", name(c.getClassLoader()));
    }

    static String name(ClassLoader l) {
        if (l == null) return "bootstrap";
        if (l == ClassLoader.getPlatformClassLoader()) return "platform";
        if (l == ClassLoader.getSystemClassLoader()) return "app";
        return "source-launcher loader";
    }
}

/* Output:
java.lang.String           module java.base            loader bootstrap
java.sql.Date              module java.sql             loader platform
java.net.http.HttpClient   module java.net.http        loader platform
this program               module (unnamed)            loader source-launcher loader

parent chain: source-launcher loader -> app -> platform -> bootstrap
app's parent is platform: true
platform's parent is bootstrap (null): true

app.loadClass("java.lang.String") == String.class: true
its defining loader: bootstrap
String.class resource protocol: jrt
unknown class: ClassNotFoundException
*/
