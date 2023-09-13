// Effective Java, Item 66: Use native methods judiciously.
//
// The Java Native Interface lets Java call C/C++ code. Historically it was
// used for platform facilities, legacy libraries and speed. Today the JDK
// covers most platform needs, and the JVM is fast enough that rewriting
// hot code natively rarely pays off. Native code is unsafe (memory
// corruption crashes the whole JVM), not portable, harder to debug, and every
// crossing between Java and native code has a cost. A native method is just
// a declaration: if no library supplies the body, the call fails at run
// time. Prefer a pure-Java library (BigInteger, not a GMP binding) unless you
// really need a native one, and then test the glue code thoroughly.
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.Arrays;

public class NativeMethods {
    // Compiles, but nothing provides the implementation: no System.loadLibrary
    static native long nativeFactorial(int n);

    public static void main(String[] args) throws Exception {
        // The JDK itself still uses native methods at its edges
        Method[] probes = {
                Object.class.getMethod("hashCode"),
                System.class.getMethod("arraycopy", Object.class, int.class, Object.class, int.class, int.class),
                Thread.class.getMethod("currentThread"),
                BigInteger.class.getMethod("multiply", BigInteger.class),
                Arrays.class.getMethod("sort", int[].class),
        };
        for (Method m : probes) {
            System.out.printf("%-22s native=%b%n",
                    m.getDeclaringClass().getSimpleName() + "." + m.getName(),
                    Modifier.isNative(m.getModifiers()));
        }

        // Anti-pattern: depending on native code that may not be there
        try {
            System.out.println(nativeFactorial(25));
        } catch (UnsatisfiedLinkError e) {
            System.out.println("nativeFactorial(25): UnsatisfiedLinkError");
        }

        // Recommended: the pure-Java library is portable, safe and fast enough
        System.out.println("BigInteger 25! = " + factorial(25));
        System.out.println("BigInteger 100! has " + factorial(100).toString().length() + " digits");
    }

    static BigInteger factorial(int n) {
        BigInteger result = BigInteger.ONE;
        for (int i = 2; i <= n; i++) {
            result = result.multiply(BigInteger.valueOf(i));
        }
        return result;
    }
}

/* Output:
Object.hashCode        native=true
System.arraycopy       native=true
Thread.currentThread   native=true
BigInteger.multiply    native=false
Arrays.sort            native=false
nativeFactorial(25): UnsatisfiedLinkError
BigInteger 25! = 15511210043330985984000000
BigInteger 100! has 158 digits
*/
