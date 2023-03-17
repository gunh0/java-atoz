// OBJ09-J. Compare classes and not class names
// https://wiki.sei.cmu.edu/confluence/display/java/OBJ09-J.+Compare+classes+and+not+class+names
//
// At run time a class is identified by its fully qualified name AND the
// class loader that defined it. Two loaders can each define a class called
// "TrustedPlugin", and they are different, incompatible classes. A check
// such as obj.getClass().getName().equals("TrustedPlugin") therefore
// accepts any class that merely carries the right name, including one from
// an attacker-controlled loader. Compare Class objects instead
// (obj.getClass() == TrustedPlugin.class, or instanceof), which takes the
// defining loader into account.
import java.io.InputStream;

public class OBJ09_CompareClasses {
    public static void main(String[] args) throws Exception {
        Object genuine = new TrustedPlugin();
        Object impostor = loadImpostor();

        System.out.println("same name:         " + genuine.getClass().getName().equals(impostor.getClass().getName()));
        System.out.println("same class:        " + (genuine.getClass() == impostor.getClass()));
        System.out.println("same loader:       " + (genuine.getClass().getClassLoader() == impostor.getClass().getClassLoader()));
        System.out.println();
        System.out.println("noncompliant, genuine trusted:  " + isTrustedNoncompliant(genuine));
        System.out.println("noncompliant, impostor trusted: " + isTrustedNoncompliant(impostor));
        System.out.println("compliant,    genuine trusted:  " + isTrustedCompliant(genuine));
        System.out.println("compliant,    impostor trusted: " + isTrustedCompliant(impostor));
    }

    // Noncompliant: only the name is compared
    static boolean isTrustedNoncompliant(Object plugin) {
        return plugin.getClass().getName().equals("TrustedPlugin");
    }

    // Compliant: the Class objects are compared, so the defining loader counts
    static boolean isTrustedCompliant(Object plugin) {
        return plugin.getClass() == TrustedPlugin.class;
    }

    // Stand-in for code from elsewhere: a separate loader defines its own
    // class named "TrustedPlugin" (here from the same bytes, for simplicity)
    static Object loadImpostor() throws Exception {
        byte[] bytes;
        try (InputStream in = OBJ09_CompareClasses.class.getResourceAsStream("TrustedPlugin.class")) {
            bytes = in.readAllBytes();
        }
        ClassLoader foreign = new ClassLoader(null) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (name.equals("TrustedPlugin")) {
                    return defineClass(name, bytes, 0, bytes.length);
                }
                throw new ClassNotFoundException(name);
            }
        };
        var constructor = foreign.loadClass("TrustedPlugin").getDeclaredConstructor();
        constructor.setAccessible(true); // the attacker's own code would simply call new
        return constructor.newInstance();
    }
}

class TrustedPlugin {
    public TrustedPlugin() {}
}

/* Output:
same name:         true
same class:        false
same loader:       false

noncompliant, genuine trusted:  true
noncompliant, impostor trusted: true
compliant,    genuine trusted:  true
compliant,    impostor trusted: false
*/
