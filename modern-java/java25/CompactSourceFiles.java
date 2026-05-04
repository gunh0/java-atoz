// JEP 512 — Compact Source Files and Instance Main Methods (Java 25)
//
// The first Java program anyone writes used to need a class, `public`,
// `static`, `String[] args` and `System.out` before printing one line:
//
//     public class Hello {
//         public static void main(String[] args) {
//             System.out.println("Hello");
//         }
//     }
//
// Since Java 25 a source file may hold just fields and methods. The compiler
// wraps them in an implicitly declared final class named after the file, in
// the unnamed package, and imports the whole java.base module for it (as if
// it said `import module java.base;`, JEP 511), so List, Map, Stream and
// friends need no imports. `main` may be an instance method with no
// parameters: the launcher creates the object with its no-arg constructor
// and calls main(). The new java.lang.IO class (println, print, readln)
// covers console I/O. This file has no class declaration at all; it is
// launched like any other: `java CompactSourceFiles.java`.

// Top-level fields and methods become members of the implicit class
final List<String> log = new ArrayList<>();

record Point(int x, int y) {
    double length() {
        return Math.hypot(x, y);
    }
}

void main() {
    IO.println("Hello from a file without a class");

    // java.base is imported on demand: no import lines above
    Map<Boolean, List<Integer>> parity = IntStream.rangeClosed(1, 8).boxed()
            .collect(Collectors.partitioningBy(n -> n % 2 == 0));
    IO.println("partitioned: " + parity);

    // main is an instance method, so it can use fields and other methods
    note("main started");
    var p = new Point(3, 4);
    note("point " + p + " has length " + p.length());
    log.forEach(line -> IO.println("log: " + line));

    // What the compiler generated
    Class<?> c = getClass();
    IO.println("implicit class name: " + c.getName());
    IO.println("package: \"" + c.getPackageName() + "\", final: " + Modifier.isFinal(c.getModifiers())
            + ", superclass: " + c.getSuperclass().getSimpleName());
    IO.println("nested record: " + Point.class.getName());
    try {
        Method m = c.getDeclaredMethod("main");
        IO.println("main is static: " + Modifier.isStatic(m.getModifiers())
                + ", public: " + Modifier.isPublic(m.getModifiers()) + ", parameters: " + m.getParameterCount());
    } catch (NoSuchMethodException e) {
        throw new AssertionError(e);
    }
}

void note(String event) {
    log.add(event);
}

/* Output:
Hello from a file without a class
partitioned: {false=[1, 3, 5, 7], true=[2, 4, 6, 8]}
log: main started
log: point Point[x=3, y=4] has length 5.0
implicit class name: CompactSourceFiles
package: "", final: true, superclass: Object
nested record: CompactSourceFiles$Point
main is static: false, public: false, parameters: 0
*/
