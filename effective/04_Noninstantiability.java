// Effective Java, Item 4: Enforce noninstantiability with a private constructor.
//
// Classes that only group static members (like java.lang.Math or
// java.util.Collections) should never be instantiated. Leaving out the
// constructor does not help: the compiler then adds a default no-arg one
// (public, for a public class).
// Making the class abstract does not help either: anyone can subclass it,
// and it misleads readers into thinking it was designed for inheritance.
// Declare a private constructor (that throws, as a guard against reflection
// and accidental internal use) and document why it exists.
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class NoninstantiabilityDemo {
    public static void main(String[] args) throws Exception {
        // Anti-pattern 1: no constructor at all -> the compiler adds a default one
        System.out.println("constructors the compiler added: "
                + ImplicitUtils.class.getDeclaredConstructors().length);
        ImplicitUtils pointless = new ImplicitUtils();
        System.out.println("created a useless " + pointless.getClass().getSimpleName());

        // Anti-pattern 2: abstract class -> a subclass instantiates it anyway
        AbstractUtils sneaky = new AbstractUtils() { };
        System.out.println("abstract 'utility' instantiated via a subclass of "
                + sneaky.getClass().getSuperclass().getSimpleName());

        // Recommended: private constructor that throws
        System.out.println("Strings.reverse(\"stressed\") = " + Strings.reverse("stressed"));
        // new Strings();  // does not compile: Strings() has private access

        Constructor<Strings> ctor = Strings.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        try {
            ctor.newInstance();
        } catch (InvocationTargetException e) {
            System.out.println("reflection blocked: " + e.getCause());
        }
    }
}

class ImplicitUtils {
    static int twice(int x) {
        return 2 * x;
    }
}

abstract class AbstractUtils {
    static int twice(int x) {
        return 2 * x;
    }
}

// Noninstantiable utility class. As a side effect it also cannot be
// subclassed: every subclass constructor must call an accessible super constructor.
final class Strings {
    // Suppress the default constructor; throwing catches internal misuse too
    private Strings() {
        throw new AssertionError("Strings is a noninstantiable utility class");
    }

    static String reverse(String s) {
        return new StringBuilder(s).reverse().toString();
    }
}

/* Output:
constructors the compiler added: 1
created a useless ImplicitUtils
abstract 'utility' instantiated via a subclass of AbstractUtils
Strings.reverse("stressed") = desserts
reflection blocked: java.lang.AssertionError: Strings is a noninstantiable utility class
*/
