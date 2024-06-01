// ERR02-J. Prevent exceptions while logging data
// https://wiki.sei.cmu.edu/confluence/display/java/ERR02-J.+Prevent+exceptions+while+logging+data
//
// Logging usually happens in exactly the places where something already
// went wrong: a failed login, a rejected request, a caught exception. If
// the logging call itself can throw (the disk is full, the log stream was
// closed, a formatter chokes on a null), that new exception replaces the
// original one and skips the rest of the handler, including the security
// response that should follow the log line. Use a logging mechanism that
// reports its own failures out of band (java.util.logging's Handlers send
// them to an ErrorManager), and never let a log statement stand between
// detecting a problem and reacting to it.
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.logging.ErrorManager;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class ERR02_ExceptionsWhileLogging {
    public static void main(String[] args) {
        AuditFile brokenDisk = new AuditFile(true);

        LoginNoncompliant bad = new LoginNoncompliant(brokenDisk);
        for (int i = 0; i < 5; i++) {
            try {
                bad.failedAttempt("mallory");
            } catch (UncheckedIOException e) {
                // the caller sees an I/O problem, not a security event
            }
        }
        System.out.println("noncompliant: failures counted " + bad.failures + ", locked: " + bad.locked);

        LoginCompliant good = new LoginCompliant(brokenDisk);
        for (int i = 0; i < 5; i++) {
            good.failedAttempt("mallory");
        }
        System.out.println("compliant:    failures counted " + good.failures + ", locked: " + good.locked
                + ", logging errors reported: " + good.loggingErrors);

        // java.util.logging: a failing Handler reports to its ErrorManager instead of throwing
        Logger logger = Logger.getLogger("err02.demo");
        logger.setUseParentHandlers(false);
        Handler failing = new Handler() {
            @Override
            public void publish(LogRecord record) {
                reportError("disk full", new IOException("disk full"), ErrorManager.WRITE_FAILURE);
            }

            @Override public void flush() {}
            @Override public void close() {}
        };
        failing.setErrorManager(new ErrorManager() {
            @Override
            public synchronized void error(String msg, Exception ex, int code) {
                System.out.println("compliant:    ErrorManager got: " + msg + " (code " + code + ")");
            }
        });
        logger.addHandler(failing);
        logger.log(Level.WARNING, "failed login for {0}", "mallory");
        System.out.println("compliant:    logger.log returned normally");
    }
}

// A log sink that fails, like a full disk
class AuditFile {
    private final boolean full;

    AuditFile(boolean full) {
        this.full = full;
    }

    void write(String line) {
        if (full) throw new UncheckedIOException(new IOException("No space left on device"));
    }
}

class LoginNoncompliant {
    final AuditFile audit;
    int failures;
    boolean locked;

    LoginNoncompliant(AuditFile audit) {
        this.audit = audit;
    }

    void failedAttempt(String user) {
        audit.write("failed login for " + user); // throws: the lines below never run
        failures++;
        if (failures >= 3) locked = true;
    }
}

class LoginCompliant {
    final AuditFile audit;
    int failures;
    boolean locked;
    int loggingErrors;

    LoginCompliant(AuditFile audit) {
        this.audit = audit;
    }

    void failedAttempt(String user) {
        failures++;                      // react first
        if (failures >= 3) locked = true;
        try {
            audit.write("failed login for " + user);
        } catch (RuntimeException e) {
            loggingErrors++;             // report the logging failure out of band
        }
    }
}

/* Output:
noncompliant: failures counted 0, locked: false
compliant:    failures counted 5, locked: true, logging errors reported: 5
compliant:    ErrorManager got: disk full (code 1)
compliant:    logger.log returned normally
*/
