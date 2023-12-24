// java.util.logging (JUL) ships with the JDK, no dependency needed:
//  - Logger: named, hierarchical ("app.auth" is a child of "app"); a record
//    below the logger's Level is dropped before any work is done
//  - Handler: where records go (ConsoleHandler -> stderr, FileHandler,
//    StreamHandler, ...), with its own Level filter
//  - Formatter: turns a LogRecord into text
// Records propagate to parent loggers' handlers unless
// setUseParentHandlers(false). Use the Supplier<String> overloads or {0}
// parameters so expensive messages are only built when actually logged, and
// never log secrets: mask them first.
// Here a handler writes to System.out with a fixed format (no timestamps), so
// the output is deterministic.
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

public class JavaUtilLoggingExample {
    static final class PlainFormatter extends Formatter {
        @Override
        public String format(LogRecord r) {
            String line = String.format("%-7s [%s] %s%n", r.getLevel(), r.getLoggerName(), formatMessage(r));
            if (r.getThrown() != null) {
                line += "        caused by " + r.getThrown().getClass().getSimpleName() + ": " + r.getThrown().getMessage() + "\n";
            }
            return line;
        }
    }

    static int expensiveCalls = 0;

    static String expensiveDump() {
        expensiveCalls++;
        return "full session dump";
    }

    static String mask(String token) {
        return token.substring(0, 4) + "****";
    }

    public static void main(String[] args) {
        Logger app = Logger.getLogger("app");
        app.setUseParentHandlers(false); // don't also print through the root ConsoleHandler
        app.setLevel(Level.INFO);
        // StreamHandler buffers; flushing per record keeps it in step with System.out
        Handler out = new StreamHandler(System.out, new PlainFormatter()) {
            @Override
            public synchronized void publish(LogRecord record) {
                super.publish(record);
                flush();
            }
        };
        out.setLevel(Level.ALL);
        app.addHandler(out);

        Logger auth = Logger.getLogger("app.auth"); // child: inherits level and handlers
        System.out.println("auth parent = " + auth.getParent().getName() + ", auth level = " + auth.getLevel()
                + " (null means inherited)");

        auth.info("service started");
        auth.log(Level.WARNING, "failed login for {0} from {1}", new Object[] {"bob", "203.0.113.9"});
        auth.fine("not shown: FINE is below INFO");
        auth.fine(() -> expensiveDump());                 // supplier is never called
        auth.info("token issued: " + mask("tok_8f2c91d0e4"));
        auth.log(Level.SEVERE, "key store unavailable", new IllegalStateException("keystore.p12 locked"));

        System.out.println("isLoggable(FINE) = " + auth.isLoggable(Level.FINE) + ", expensive calls = " + expensiveCalls);

        // Turning on debug output for one subsystem only
        auth.setLevel(Level.FINE);
        auth.fine(() -> expensiveDump());
        Logger.getLogger("app.billing").fine("still hidden: app.billing uses app's INFO level");
        System.out.println("expensive calls = " + expensiveCalls);
    }
}

/* Output:
auth parent = app, auth level = null (null means inherited)
INFO    [app.auth] service started
WARNING [app.auth] failed login for bob from 203.0.113.9
INFO    [app.auth] token issued: tok_****
SEVERE  [app.auth] key store unavailable
        caused by IllegalStateException: keystore.p12 locked
isLoggable(FINE) = false, expensive calls = 0
FINE    [app.auth] full session dump
expensive calls = 1
*/
