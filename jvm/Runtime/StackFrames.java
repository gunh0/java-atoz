// JVM stack frames: what a stack walk shows, what it hides, and how deep it goes
//
// Every thread has its own JVM stack of frames, one per active method
// invocation, each holding local variables, an operand stack and the
// current bytecode index (BCI). StackWalker (modern-java/java9) reads them;
// this file looks at what the JVM itself adds. By default a walk hides
// infrastructure frames: reflection machinery (Method.invoke and the
// accessors behind it) and the frames of hidden classes such as lambda
// implementations; SHOW_REFLECT_FRAMES and SHOW_HIDDEN_FRAMES reveal them,
// which matters for caller-sensitive security checks that must not be
// fooled by a reflective or lambda hop. Each frame knows its BCI and the
// method's descriptor. The stack's size is fixed per thread (-Xss, or the
// Thread constructor's stackSize hint); overflowing it throws
// StackOverflowError, after which the thread can continue normally.
import java.lang.StackWalker.Option;
import java.lang.StackWalker.StackFrame;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class StackFrames {
    public static void main(String[] args) throws Exception {
        // A call that goes through reflection, then a lambda
        Method m = StackFrames.class.getDeclaredMethod("viaLambda");
        m.invoke(null);

        // Stack depth: recurse until the stack is full, then carry on
        int[] depth = new int[1];
        Thread t = new Thread(null, () -> {
            try {
                recurse(depth);
            } catch (StackOverflowError e) {
                System.out.println("StackOverflowError caught after more than 1000 frames: " + (depth[0] > 1000));
            }
            System.out.println("the same thread keeps running afterwards: " + Thread.currentThread().getName());
        }, "small-stack", 256 * 1024); // requested stack size: a hint the JVM may round
        t.start();
        t.join();
    }

    static void viaLambda() {
        Supplier<String> s = () -> {
            report("default", StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE));
            report("SHOW_REFLECT_FRAMES", StackWalker.getInstance(Set.of(Option.RETAIN_CLASS_REFERENCE, Option.SHOW_REFLECT_FRAMES)));
            report("SHOW_HIDDEN_FRAMES", StackWalker.getInstance(Set.of(Option.RETAIN_CLASS_REFERENCE, Option.SHOW_HIDDEN_FRAMES)));

            StackFrame top = StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE).walk(f -> f.findFirst()).orElseThrow();
            // javac names lambda bodies lambda$<method>$<n>; n is a compiler detail
            System.out.println("top frame: " + top.getMethodName().replaceAll("\\$\\d+$", "\\$n") + ", descriptor "
                    + top.getDescriptor() + ", has a bytecode index: " + (top.getByteCodeIndex() >= 0));
            return "";
        };
        s.get();
    }

    static void report(String label, StackWalker walker) {
        List<StackFrame> frames = walker.walk(f -> f.toList());
        boolean reflection = frames.stream().anyMatch(f -> f.getDeclaringClass() == Method.class);
        boolean hidden = frames.stream().anyMatch(f -> f.getDeclaringClass().isHidden());
        System.out.printf("%-20s Method.invoke frame visible: %-5b lambda (hidden class) frame visible: %b%n",
                label + ":", reflection, hidden);
    }

    static void recurse(int[] depth) {
        depth[0]++;
        recurse(depth);
    }
}

/* Output:
default:             Method.invoke frame visible: false lambda (hidden class) frame visible: false
SHOW_REFLECT_FRAMES: Method.invoke frame visible: true  lambda (hidden class) frame visible: false
SHOW_HIDDEN_FRAMES:  Method.invoke frame visible: true  lambda (hidden class) frame visible: true
top frame: lambda$viaLambda$n, descriptor ()Ljava/lang/String;, has a bytecode index: true
StackOverflowError caught after more than 1000 frames: true
the same thread keeps running afterwards: small-stack
*/
