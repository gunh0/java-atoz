// The five invoke instructions and how javac chooses between them
//
// Every method call in Java source becomes one of five bytecodes:
//   invokestatic    static methods (on classes, and on interfaces since Java 8);
//   invokevirtual   instance methods of a class: dispatched on the receiver's
//                   runtime class through the vtable;
//   invokeinterface instance methods called through an interface type: the
//                   receiver's class may implement it anywhere in its hierarchy,
//                   so lookup is more general (itable);
//   invokespecial   no dynamic dispatch: constructors (<init>), super.m()
//                   and Iface.super.m(). Private methods used it too until
//                   javac 11; with nestmates (JEP 181) they are invokevirtual
//                   (invokeinterface in interfaces), and the JVM simply finds
//                   that a private method cannot be overridden;
//   invokedynamic   the call site is linked by a bootstrap method at first
//                   execution (lambdas, string concatenation, pattern switch).
// The difference is observable: super.describe() runs the parent's code even
// though the object's class overrides it, while this.describe() dispatches.
// The program compiles a small class in memory, lists the invoke instructions
// of one method in order, then runs the same calls.
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

public class InvokeInstructions {
    static final String SOURCE = """
            import java.util.*;
            import java.util.function.*;
            interface Greeter { default String hello() { return "hello"; } static Greeter create() { return new Greeter() {}; } }
            class Base { String describe() { return "Base"; } }
            public class Calls extends Base implements Greeter {
                @Override String describe() { return "Calls"; }
                private String secret() { return "s"; }
                public String hello() { return Greeter.super.hello() + "!"; }
                String demo(List<String> list) {
                    Calls c = new Calls();                    // new + invokespecial <init>
                    String a = c.describe();                  // invokevirtual
                    String b = super.describe();              // invokespecial (no dispatch)
                    int n = list.size();                      // invokeinterface
                    String d = String.valueOf(n);             // invokestatic
                    Greeter g = Greeter.create();             // invokestatic on an interface
                    String e = secret();                      // private: invokevirtual since 11
                    Supplier<String> s = () -> "lazy";        // invokedynamic
                    return a + b + d + g.hello() + e + s.get();
                }
            }
            """;

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("invoke");
        try {
            Path src = dir.resolve("Calls.java");
            Files.writeString(src, SOURCE);
            tool("javac", "--release", "17", "-d", dir.toString(), src.toString());
            String javap = tool("javap", "-c", "-p", dir.resolve("Calls.class").toString());
            boolean in = false;
            for (String line : javap.lines().toList()) {
                String t = line.strip();
                if (t.endsWith(");")) in = t.contains(" demo(");
                else if (in && t.matches("\\d+: invoke.*")) {
                    String op = t.replaceFirst("^\\d+: (\\w+).*", "$1");
                    String target = t.substring(t.indexOf("// ") + 3)
                            .replaceFirst("^(Method|InterfaceMethod|InvokeDynamic) ", "").replaceFirst("#\\d+:", "");
                    System.out.printf("%-16s %s%n", op, target);
                }
            }
        } finally {
            try (Stream<Path> s = Files.walk(dir)) {
                for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }

        // Dispatch, observed: same object, different instructions
        Child child = new Child();
        System.out.println("virtual call on Child:      " + child.viaVirtual());
        System.out.println("super call inside Child:    " + child.viaSuper());
        Supplier<String> lambda = () -> "linked by a bootstrap method";
        List<String> list = List.of("x");
        System.out.println("interface call, List.size(): " + list.size());
        System.out.println("invokedynamic: " + lambda.get());
    }

    static class Parent { String name() { return "Parent.name"; } }

    static class Child extends Parent {
        @Override String name() { return "Child.name"; }
        String viaVirtual() { return this.name(); }   // invokevirtual
        String viaSuper() { return super.name(); }    // invokespecial
    }

    static String tool(String name, String... args) {
        StringWriter out = new StringWriter();
        int rc = ToolProvider.findFirst(name).orElseThrow().run(new PrintWriter(out), new PrintWriter(out), args);
        if (rc != 0) throw new IllegalStateException(name + " failed:\n" + out);
        return out.toString();
    }
}

/* Output:
invokespecial    "<init>":()V
invokevirtual    describe:()Ljava/lang/String;
invokespecial    Base.describe:()Ljava/lang/String;
invokeinterface  java/util/List.size:()I
invokestatic     java/lang/String.valueOf:(I)Ljava/lang/String;
invokestatic     Greeter.create:()LGreeter;
invokevirtual    secret:()Ljava/lang/String;
invokedynamic    get:()Ljava/util/function/Supplier;
invokeinterface  Greeter.hello:()Ljava/lang/String;
invokeinterface  java/util/function/Supplier.get:()Ljava/lang/Object;
invokedynamic    makeConcatWithConstants:(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
virtual call on Child:      Child.name
super call inside Child:    Parent.name
interface call, List.size(): 1
invokedynamic: linked by a bootstrap method
*/
