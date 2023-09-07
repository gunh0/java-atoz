// Effective Java, Item 65: Prefer interfaces to reflection.
//
// Core reflection (java.lang.reflect) lets code use classes that did not
// exist when it was compiled, but at a price: no compile-time type checking,
// so typos and wrong signatures surface only as exceptions at run time;
// verbose, exception-heavy code; and slower calls. The sweet spot is to use
// reflection only to *create* an instance of a class chosen at run time, and
// then talk to it through an interface or superclass known at compile time.
// Everything after the constructor call is ordinary, checked code.
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

public class InterfacesOverReflection {
    public static void main(String[] args) {
        String[] words = {"pear", "apple", "fig", "apple", "banana"};

        // Anti-pattern: drive the object entirely through reflection
        try {
            Object set = Class.forName("java.util.TreeSet").getDeclaredConstructor().newInstance();
            Method add = set.getClass().getMethod("add", Object.class);
            for (String w : words) {
                add.invoke(set, w);
            }
            System.out.println("reflective calls: " + set);
            // A typo compiles fine and fails only when this line runs
            set.getClass().getMethod("addd", Object.class);
        } catch (NoSuchMethodException e) {
            System.out.println("  run-time failure: NoSuchMethodException " + e.getMessage());
        } catch (ReflectiveOperationException e) {
            System.out.println("  run-time failure: " + e);
        }

        // Recommended: reflection only for instantiation, then use the interface
        for (String className : new String[] {"java.util.TreeSet", "java.util.LinkedHashSet",
                "java.util.HashMap", "java.util.NoSuchSet"}) {
            Set<String> s = newSet(className);
            if (s == null) {
                continue;
            }
            for (String w : words) {
                s.add(w); // checked by the compiler
            }
            System.out.println(className + " via Set: " + s);
        }
    }

    // All the reflective noise is confined to this one method
    static Set<String> newSet(String className) {
        try {
            Class<? extends Set<String>> cl = asSetClass(Class.forName(className));
            return cl.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            System.out.println(className + ": class not found");
        } catch (ClassCastException e) {
            System.out.println(className + ": not a Set");
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException
                | InvocationTargetException e) {
            System.out.println(className + ": cannot instantiate (" + e.getClass().getSimpleName() + ")");
        }
        return null;
    }

    @SuppressWarnings("unchecked") // asSubclass checked the raw type
    private static Class<? extends Set<String>> asSetClass(Class<?> c) {
        return (Class<? extends Set<String>>) c.asSubclass(Set.class);
    }
}

/* Output:
reflective calls: [apple, banana, fig, pear]
  run-time failure: NoSuchMethodException java.util.TreeSet.addd(java.lang.Object)
java.util.TreeSet via Set: [apple, banana, fig, pear]
java.util.LinkedHashSet via Set: [pear, apple, fig, banana]
java.util.HashMap: not a Set
java.util.NoSuchSet: class not found
*/
