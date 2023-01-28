// Effective Java, Item 39: Prefer annotations to naming patterns.
//
// Old frameworks found the methods they should treat specially by name, e.g.
// "every method starting with test". A typo (tsetSomething) silently turns a
// test off, the convention cannot be restricted to the right kind of program
// element, and there is no way to attach a parameter (such as the exception a
// test is expected to throw). An annotation fixes all three: it is checked by
// the compiler, @Target limits where it may appear, and its members carry
// typed parameters. Below, the same tiny test runner is written both ways.
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;

public class AnnotationsOverNamingPatterns {
    public static void main(String[] args) throws Exception {
        System.out.println("-- naming pattern runner on NamedTests --");
        runByName(NamedTests.class);

        System.out.println("-- annotation runner on AnnotatedTests --");
        runByAnnotation(AnnotatedTests.class);
    }

    // Naming pattern: anything called test* is a test, nothing else is.
    static void runByName(Class<?> testClass) throws Exception {
        for (Method m : sortedMethods(testClass)) {
            if (m.getName().startsWith("test")) {
                runOne(m, null);
            } else {
                System.out.println("  skipped " + m.getName() + " (name does not match)");
            }
        }
    }

    // Annotations: the marker says "test"; ExceptionTest also says which exception is expected.
    static void runByAnnotation(Class<?> testClass) throws Exception {
        int tests = 0, passed = 0;
        for (Method m : sortedMethods(testClass)) {
            if (m.isAnnotationPresent(Test.class)) {
                tests++;
                passed += runOne(m, null) ? 1 : 0;
            } else if (m.isAnnotationPresent(ExceptionTest.class)) {
                tests++;
                passed += runOne(m, m.getAnnotation(ExceptionTest.class).value()) ? 1 : 0;
            }
        }
        System.out.printf("  passed: %d, failed: %d%n", passed, tests - passed);
    }

    static boolean runOne(Method m, Class<? extends Throwable>[] expected) throws IllegalAccessException {
        try {
            m.invoke(null);
            if (expected == null) {
                System.out.println("  " + m.getName() + ": pass");
                return true;
            }
            System.out.println("  " + m.getName() + ": FAIL, no exception thrown");
        } catch (InvocationTargetException wrapped) {
            Throwable exc = wrapped.getCause();
            if (expected != null && Arrays.stream(expected).anyMatch(t -> t.isInstance(exc))) {
                System.out.println("  " + m.getName() + ": pass (threw " + exc.getClass().getSimpleName() + ")");
                return true;
            }
            System.out.println("  " + m.getName() + ": FAIL, " + exc);
        }
        return false;
    }

    // getDeclaredMethods() has no defined order; sort for a stable report.
    static Method[] sortedMethods(Class<?> c) {
        Method[] ms = c.getDeclaredMethods();
        Arrays.sort(ms, Comparator.comparing(Method::getName));
        return Arrays.stream(ms).filter(m -> Modifier.isStatic(m.getModifiers())).toArray(Method[]::new);
    }
}

/** Marks a static, parameterless method as a test. */
@Retention(RetentionPolicy.RUNTIME) // must survive to run time for reflection
@Target(ElementType.METHOD)         // the compiler rejects it on a field or class
@interface Test {
}

/** A test that passes only if it throws one of the given exception types. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface ExceptionTest {
    Class<? extends Throwable>[] value();
}

class NamedTests {
    static void testAddition() {
        if (1 + 1 != 2) throw new AssertionError();
    }

    // Typo: this test never runs, and nothing tells you.
    static void tsetDivision() {
        throw new AssertionError("this failure is never reported");
    }

    // No way to say "this should throw ArithmeticException"; it just fails.
    static void testDivideByZero() {
        int zero = 0;
        System.out.println(1 / zero);
    }
}

class AnnotatedTests {
    @Test
    static void addition() {
        if (1 + 1 != 2) throw new AssertionError();
    }

    @Test
    static void division() {
        throw new AssertionError("now this failure is reported");
    }

    @ExceptionTest(ArithmeticException.class)
    static void divideByZero() {
        int zero = 0;
        System.out.println(1 / zero);
    }

    @ExceptionTest({ IndexOutOfBoundsException.class, NullPointerException.class })
    static void outOfBounds() {
        int[] empty = new int[0];
        empty[0] = 1;
    }

    static void helper() { // not annotated: not a test, whatever its name
    }
}

/* Output:
-- naming pattern runner on NamedTests --
  testAddition: pass
  testDivideByZero: FAIL, java.lang.ArithmeticException: / by zero
  skipped tsetDivision (name does not match)
-- annotation runner on AnnotatedTests --
  addition: pass
  divideByZero: pass (threw ArithmeticException)
  division: FAIL, java.lang.AssertionError: now this failure is reported
  outOfBounds: pass (threw ArrayIndexOutOfBoundsException)
  passed: 3, failed: 1
*/
