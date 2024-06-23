// How a lambda becomes an object: invokedynamic + LambdaMetafactory
//
// javac does not generate a class per lambda. It moves the lambda body into
// a private synthetic method (lambda$main$0 or similar) and emits an invokedynamic
// instruction at the place the lambda appears. The first time that
// instruction runs, the JVM calls its bootstrap method,
// LambdaMetafactory.metafactory, which spins a small hidden class
// implementing the functional interface and returns a CallSite; later
// executions reuse the linked call site. A non-capturing lambda therefore
// yields one shared instance in HotSpot, a capturing one a new object per
// evaluation, and no lambda has a usable class name. Below, the same
// machinery is driven by hand: a MethodHandle to a static method is turned
// into a Function by calling the metafactory directly.
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Function;
import java.util.function.Supplier;

public class LambdaMetafactoryDemo {
    static String shout(String s) { return s.toUpperCase() + "!"; }

    static class Sample {
        Runnable noop() { return () -> { }; }
        Supplier<String> greeting(String name) { return () -> "hi " + name; } // captured value becomes a parameter
        Function<String, String> trim() { return String::trim; }
    }

    public static void main(String[] args) throws Throwable {
        // 1. What javac generated for Sample: a method per lambda body, none
        //    for the method reference (it points at String.trim directly)
        Method[] methods = Sample.class.getDeclaredMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName)); // reflection order is unspecified
        for (Method m : methods) {
            if (!m.getName().startsWith("lambda$")) continue;
            // the trailing counter is a javac detail, so it is masked here
            System.out.println("Sample has " + m.getName().replaceAll("\\$\\d+$", "\\$n") + Arrays.toString(m.getParameterTypes()).replace("class java.lang.", "")
                    + ", private static synthetic: " + (m.isSynthetic()
                    && Modifier.isPrivate(m.getModifiers()) && Modifier.isStatic(m.getModifiers())));
        }

        // 2. The objects produced at run time
        Supplier<String> first = null;
        for (int i = 0; i < 2; i++) {
            Supplier<String> s = () -> "constant";                 // non-capturing
            if (first == null) first = s;
            else System.out.println("non-capturing lambda, same instance each time: " + (s == first));
        }
        String name = "duke";
        Supplier<String> c1 = () -> "hi " + name;                  // captures 'name'
        Supplier<String> c2 = () -> "hi " + name;
        System.out.println("capturing lambdas are distinct objects: " + (c1 != c2));
        Class<?> lambdaClass = first.getClass();
        System.out.println("lambda class is hidden: " + lambdaClass.isHidden()
                + ", synthetic: " + lambdaClass.isSynthetic() + ", name contains $$Lambda: " + lambdaClass.getName().contains("$$Lambda"));
        try {
            Class.forName(lambdaClass.getName());
        } catch (ClassNotFoundException e) {
            System.out.println("Class.forName(lambda class name): " + e.getClass().getSimpleName());
        }

        // 3. Doing the invokedynamic linkage by hand
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodHandle impl = lookup.findStatic(LambdaMetafactoryDemo.class, "shout", MethodType.methodType(String.class, String.class));
        CallSite site = LambdaMetafactory.metafactory(
                lookup,
                "apply",                                            // interface method name
                MethodType.methodType(Function.class),              // factory type: no captured values
                MethodType.methodType(Object.class, Object.class),  // erased Function.apply signature
                impl,                                               // implementation
                MethodType.methodType(String.class, String.class)); // instantiated signature
        @SuppressWarnings("unchecked")
        Function<String, String> f = (Function<String, String>) site.getTarget().invokeExact();
        System.out.println("hand-made Function: " + f.apply("indy") + ", hidden class: " + f.getClass().isHidden());
    }
}

/* Output:
Sample has lambda$greeting$n[String], private static synthetic: true
Sample has lambda$noop$n[], private static synthetic: true
non-capturing lambda, same instance each time: true
capturing lambdas are distinct objects: true
lambda class is hidden: true, synthetic: true, name contains $$Lambda: true
Class.forName(lambda class name): ClassNotFoundException
hand-made Function: INDY!, hidden class: true
*/
