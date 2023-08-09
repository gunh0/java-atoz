// JEP 403 — Strongly Encapsulate JDK Internals (Java 17)
//
// Java 9 put the JDK into modules, but for compatibility it still let code
// on the class path reflect into non-public JDK internals ("illegal
// reflective access"), first with a warning, from Java 16 denied by default
// but switchable back with --illegal-access=permit. Libraries kept relying
// on it: poking private fields of String, calling sun.* classes, patching
// security-sensitive state. Java 17 removes the switch: setAccessible(true)
// on a non-public member of a package that java.base does not open throws
// InaccessibleObjectException, and non-exported internal packages cannot be
// used even through public methods. The only ways in are the supported
// APIs or an explicit, visible --add-opens / --add-exports on the command
// line. sun.misc.Unsafe (module jdk.unsupported) stays reachable on purpose.
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Method;

public class StrongEncapsulation {
    private String secret = "own private field";

    public static void main(String[] args) throws Exception {
        // Reflection into your own code still works as before
        Field own = StrongEncapsulation.class.getDeclaredField("secret");
        own.setAccessible(true);
        System.out.println("own class: " + own.get(new StrongEncapsulation()));

        // Before Java 17 (with --illegal-access=permit) this exposed String's backing array,
        // letting any library mutate an "immutable" String. Now it is refused.
        Field value = String.class.getDeclaredField("value");
        try {
            value.setAccessible(true);
        } catch (InaccessibleObjectException e) {
            System.out.println("String.value: " + e.getClass().getSimpleName());
        }
        System.out.println("trySetAccessible on String.value: " + value.trySetAccessible());

        // The module system says why: java.base does not open java.lang to us
        Module base = String.class.getModule();
        Module me = StrongEncapsulation.class.getModule();
        System.out.println("java.lang exported to us: " + base.isExported("java.lang", me)
                + ", opened to us: " + base.isOpen("java.lang", me));

        // Internal packages are not even exported: public members are off limits too
        Class<?> internal = Class.forName("sun.security.util.Debug");
        System.out.println(internal.getPackageName() + " exported to us: " + base.isExported(internal.getPackageName(), me));
        try {
            Method m = internal.getMethod("isOn", String.class);
            m.invoke(null, "all");
        } catch (IllegalAccessException e) {
            System.out.println("public method of an internal class: IllegalAccessException");
        }

        // sun.misc.Unsafe is the deliberate exception (jdk.unsupported opens sun.misc)
        Field theUnsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        System.out.println("sun.misc.Unsafe.theUnsafe accessible: " + theUnsafe.trySetAccessible());
    }
}

/* Output:
own class: own private field
String.value: InaccessibleObjectException
trySetAccessible on String.value: false
java.lang exported to us: true, opened to us: false
sun.security.util exported to us: false
public method of an internal class: IllegalAccessException
sun.misc.Unsafe.theUnsafe accessible: true
*/
