// JEP 259 — Stack-Walking API (Java 9)
//
// The old ways to inspect the call stack, Thread.getStackTrace() and
// new Throwable().getStackTrace(), capture EVERY frame eagerly as an array
// of StackTraceElement, which is costly when you only need the top few
// frames, and they expose only class NAMES. StackWalker walks the stack
// lazily as a Stream<StackFrame>, so you can stop early, filter, and (with
// RETAIN_CLASS_REFERENCE) get the actual Class objects, including the
// direct caller via getCallerClass(). Logging frameworks and security
// checks ("who called me?") are the typical users.
import java.util.List;
import java.util.stream.Collectors;

public class StackWalkerApi {
    public static void main(String[] args) {
        new Service().handle();
    }

    static class Service {
        void handle() {
            new Repository().load();
        }
    }

    static class Repository {
        void load() {
            // Before: the whole stack, eagerly, names only
            StackTraceElement[] all = new Throwable().getStackTrace();
            System.out.println("before: top frame " + all[0].getMethodName() + ", caller class name "
                    + all[1].getClassName());

            // After: lazily take just the top three frames
            List<String> top = StackWalker.getInstance().walk(frames -> frames
                    .limit(3)
                    .map(f -> f.getClassName() + "." + f.getMethodName())
                    .collect(Collectors.toList()));
            System.out.println("after: top frames " + top);

            Audit.check();
        }
    }

    static class Audit {
        private static final StackWalker WALKER =
                StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

        static void check() {
            // The real Class object of whoever called us
            Class<?> caller = WALKER.getCallerClass();
            System.out.println("getCallerClass: " + caller.getSimpleName()
                    + ", trusted: " + (caller == Repository.class));

            // Find the first frame that matches a condition, then stop walking
            String entry = WALKER.walk(frames -> frames
                    .filter(f -> f.getDeclaringClass() == Service.class)
                    .map(StackWalker.StackFrame::getMethodName)
                    .findFirst()
                    .orElse("none"));
            System.out.println("first Service frame: " + entry);
        }
    }
}

/* Output:
before: top frame load, caller class name StackWalkerApi$Service
after: top frames [StackWalkerApi$Repository.load, StackWalkerApi$Service.handle, StackWalkerApi.main]
getCallerClass: Repository, trusted: true
first Service frame: handle
*/
