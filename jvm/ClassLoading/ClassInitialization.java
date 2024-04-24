// Loading vs initialization: when do static initializers run?
//
// A class goes through loading (find the bytes, create the Class object),
// linking (verification, preparation of static fields with default values,
// resolution) and initialization (running static initializers and static
// field assignments, in textual order). JLS 12.4.1 fixes WHEN
// initialization happens: right before the first instance creation, static
// method call, or use of a non-constant static field; not when the class is
// merely loaded, referenced as a type, or used for a compile-time constant
// (those are inlined into the caller by javac). Initialization runs once,
// under a lock; if it throws, the caller gets ExceptionInInitializerError and
// every later use gets NoClassDefFoundError: the class is unusable for the
// rest of the JVM's life. The lazy holder idiom relies on these rules.
import java.util.ArrayList;
import java.util.List;

public class ClassInitialization {
    static final List<String> LOG = new ArrayList<>();
    static Object sink; // keeps reads from being "unused"

    static class Config {
        static final String VERSION = "1.0";          // compile-time constant: inlined
        static final long STARTED = System.nanoTime(); // not a constant: needs initialization
        static { LOG.add("Config initialized"); }
        static void touch() { }
    }

    static class Parent { static { LOG.add("Parent initialized"); } static int p = 1; }
    static class Child extends Parent { static { LOG.add("Child initialized"); } static int c = 2; }

    static class Broken {
        static final int VALUE = Integer.parseInt("not a number"); // throws during initialization
    }

    static class Holder { // lazy, thread-safe singleton without locks
        static { LOG.add("Holder initialized"); }
        static final Object INSTANCE = new Object();
    }

    public static void main(String[] args) throws Exception {
        step("reference the type only", () -> { Config[] arr = new Config[3]; });
        step("read a compile-time constant", () -> sink = Config.VERSION);
        step("Class.forName(..., initialize=false)",
                () -> Class.forName("ClassInitialization$Config", false, ClassInitialization.class.getClassLoader()));
        step("call a static method", Config::touch);
        step("call it again", Config::touch);

        // A superclass is initialized first; reading a field declared in
        // Parent through Child initializes only Parent
        step("read Child.p (declared in Parent)", () -> sink = Child.p);
        step("read Child.c", () -> sink = Child.c);

        step("load Holder class literal", () -> sink = Holder.class);
        step("use Holder.INSTANCE", () -> sink = Holder.INSTANCE);

        // Failing initializer
        for (int i = 1; i <= 2; i++) {
            try {
                sink = Broken.VALUE;
            } catch (Throwable t) {
                System.out.println("use of Broken #" + i + ": " + t.getClass().getSimpleName()
                        + (t instanceof ExceptionInInitializerError ? " caused by " + t.getCause().getClass().getSimpleName() : ""));
            }
        }
    }

    interface Action { void run() throws Exception; }

    static void step(String label, Action a) throws Exception {
        LOG.clear();
        a.run();
        System.out.printf("%-38s -> %s%n", label, LOG.isEmpty() ? "(nothing initialized)" : String.join(", ", LOG));
    }
}

/* Output:
reference the type only                -> (nothing initialized)
read a compile-time constant           -> (nothing initialized)
Class.forName(..., initialize=false)   -> (nothing initialized)
call a static method                   -> Config initialized
call it again                          -> (nothing initialized)
read Child.p (declared in Parent)      -> Parent initialized
read Child.c                           -> Child initialized
load Holder class literal              -> (nothing initialized)
use Holder.INSTANCE                    -> Holder initialized
use of Broken #1: ExceptionInInitializerError caused by NumberFormatException
use of Broken #2: NoClassDefFoundError
*/
