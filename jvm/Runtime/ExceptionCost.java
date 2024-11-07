// What makes exceptions expensive: capturing the stack trace
//
// Throwing is cheap; CONSTRUCTING a Throwable is not, because its
// constructor calls fillInStackTrace(), which walks the current thread's
// stack and records every frame. The cost therefore grows with stack depth,
// which is large in framework-heavy code, and is paid even if nobody ever
// looks at the trace. Exceptions used for control flow on a hot path
// (parsing with NumberFormatException, "not found" signals) pay it every
// time. Options: return a result type instead; or, for a deliberate
// stackless exception, use the protected constructor with
// writableStackTrace = false (or override fillInStackTrace). HotSpot also
// caps traces at -XX:MaxJavaStackTraceDepth (1024 frames by default) and,
// for some implicit exceptions in very hot compiled code, may throw a
// preallocated instance without a trace (-XX:-OmitStackTraceInFastThrow
// disables that). This program measures cost in frames captured, not time.
public class ExceptionCost {
    static class Normal extends RuntimeException {
        Normal() { super("normal"); }
    }

    static class Stackless extends RuntimeException {
        Stackless() { super("stackless", null, false, false); } // no suppression, no stack trace
    }

    public static void main(String[] args) {
        // The frames below main (launcher, reflection) vary, so compare with depth 1
        int base = atDepth(1, Normal::new).getStackTrace().length;
        for (int depth : new int[] {10, 100, 1000}) {
            int captured = atDepth(depth, Normal::new).getStackTrace().length;
            System.out.printf("created %4d calls deeper: %4d more frames captured%n", depth - 1, captured - base);
        }
        System.out.println("capped at the default MaxJavaStackTraceDepth (1024): "
                + (atDepth(3000, Normal::new).getStackTrace().length == 1024));

        RuntimeException stackless = atDepth(1000, Stackless::new);
        System.out.println("stackless exception at depth 1000: " + stackless.getStackTrace().length + " frames");
        // ...and its trace cannot be set later either
        stackless.setStackTrace(new StackTraceElement[] {new StackTraceElement("X", "y", "X.java", 1)});
        System.out.println("after setStackTrace: " + stackless.getStackTrace().length + " frames (writableStackTrace = false)");

        // A reused, preallocated exception records the stack of its creation site only
        RuntimeException shared = new Normal();
        int sharedFrames = shared.getStackTrace().length;
        RuntimeException thrown = atDepth(500, () -> shared);
        System.out.println("preallocated exception rethrown at depth 500 still shows "
                + (thrown.getStackTrace().length == sharedFrames ? "its creation site" : "the throw site"));
    }

    interface Factory { RuntimeException create(); }

    // Recurse to the requested depth, then create (and return) an exception there
    static RuntimeException atDepth(int depth, Factory f) {
        return depth <= 1 ? f.create() : atDepth(depth - 1, f);
    }
}

/* Output:
created    9 calls deeper:    9 more frames captured
created   99 calls deeper:   99 more frames captured
created  999 calls deeper:  999 more frames captured
capped at the default MaxJavaStackTraceDepth (1024): true
stackless exception at depth 1000: 0 frames
after setStackTrace: 0 frames (writableStackTrace = false)
preallocated exception rethrown at depth 500 still shows its creation site
*/
