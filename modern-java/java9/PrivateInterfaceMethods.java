// JEP 213 — Milling Project Coin: private interface methods (Java 9)
//
// Java 8 let interfaces carry default and static methods, but every helper
// they shared had to be public (and therefore part of the API) or duplicated.
// Since Java 9 an interface may declare private instance methods (usable from
// default methods) and private static methods (usable from any interface
// method), so common code can be factored out without leaking it to callers
// or implementers.
public class PrivateInterfaceMethods {
    public static void main(String[] args) {
        Logger logger = message -> System.out.println(message);

        logger.info("server started");
        logger.warn("disk usage at 91%");
        logger.error("connection refused");

        System.out.println(Logger.format("AUDIT", "user logged in"));

        // The private helpers are not part of the type: these would not compile
        // logger.log("X", "y");
        // Logger.prefix("X");
    }
}

interface Logger {
    // The single abstract method: where a line ends up
    void write(String line);

    default void info(String message) {
        log("INFO", message);
    }

    default void warn(String message) {
        log("WARN", message);
    }

    default void error(String message) {
        log("ERROR", message);
    }

    static String format(String level, String message) {
        return prefix(level) + message;
    }

    // Before Java 9 this had to be a public default method, so any caller could
    // invoke logger.log(...) and any implementation could override it.
    private void log(String level, String message) {
        write(format(level, message));
    }

    // A private static helper, shared by static and default methods
    private static String prefix(String level) {
        return String.format("[%-5s] ", level);
    }
}

/* Output:
[INFO ] server started
[WARN ] disk usage at 91%
[ERROR] connection refused
[AUDIT] user logged in
*/
