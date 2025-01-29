// ERR05-J. Do not let checked exceptions escape from a finally block
// https://wiki.sei.cmu.edu/confluence/display/java/ERR05-J.+Do+not+let+checked+exceptions+escape+from+a+finally+block
//
// When a finally block throws, the exception that was already propagating
// from the try block is discarded without a trace. The classic case is
// close() in finally: the real failure ("signature invalid", "quota
// exceeded") is replaced by a secondary "close failed", so the caller
// handles the wrong problem, the audit log records the wrong event and a
// security decision may be skipped. Handle exceptions inside the finally
// block, or better, use try-with-resources: it closes the resource and
// attaches a close() failure to the primary exception as SUPPRESSED, so
// both are kept and the primary one wins.
import java.io.IOException;
import java.util.Arrays;

public class ERR05_CheckedExceptionsFromFinally {
    // The primary, security-relevant failure
    static class SignatureException extends Exception {
        SignatureException(String message) {
            super(message);
        }
    }

    // A staging area whose close() also fails (disk full, broken pipe, ...)
    static class Staging implements AutoCloseable {
        void store(String upload) throws SignatureException {
            if (!upload.endsWith("#signed")) {
                throw new SignatureException("upload rejected: signature invalid");
            }
        }

        @Override
        public void close() throws IOException {
            throw new IOException("staging area: flush on close failed");
        }
    }

    // Noncompliant: an exception from close() escapes the finally block
    static void importNoncompliant(String upload) throws SignatureException, IOException {
        Staging staging = new Staging();
        try {
            staging.store(upload);
        } finally {
            staging.close();
        }
    }

    // Compliant: try-with-resources keeps the primary exception
    static void importTryWithResources(String upload) throws SignatureException, IOException {
        try (Staging staging = new Staging()) {
            staging.store(upload);
        }
    }

    // Compliant without try-with-resources: deal with close() inside finally
    static void importHandledInFinally(String upload) throws SignatureException {
        Staging staging = new Staging();
        try {
            staging.store(upload);
        } finally {
            try {
                staging.close();
            } catch (IOException e) {
                System.out.println("    (cleanup problem reported separately: " + e.getMessage() + ")");
            }
        }
    }

    interface Import {
        void run(String upload) throws Exception;
    }

    public static void main(String[] args) {
        String tampered = "payroll.csv#tampered";
        attempt("noncompliant", ERR05_CheckedExceptionsFromFinally::importNoncompliant, tampered);
        attempt("try-with-resources", ERR05_CheckedExceptionsFromFinally::importTryWithResources, tampered);
        attempt("handled in finally", ERR05_CheckedExceptionsFromFinally::importHandledInFinally, tampered);
    }

    static void attempt(String label, Import importer, String upload) {
        System.out.println(label + ":");
        try {
            importer.run(upload);
            System.out.println("  imported");
        } catch (SignatureException e) {
            System.out.println("  caller sees SignatureException: " + e.getMessage() + " -> alert security team");
            Arrays.stream(e.getSuppressed())
                    .forEach(s -> System.out.println("  suppressed " + s.getClass().getSimpleName() + ": " + s.getMessage()));
        } catch (Exception e) {
            System.out.println("  caller sees " + e.getClass().getSimpleName() + ": " + e.getMessage()
                    + " -> retry later (the tampering is never reported)");
        }
    }
}

/* Output:
noncompliant:
  caller sees IOException: staging area: flush on close failed -> retry later (the tampering is never reported)
try-with-resources:
  caller sees SignatureException: upload rejected: signature invalid -> alert security team
  suppressed IOException: staging area: flush on close failed
handled in finally:
    (cleanup problem reported separately: staging area: flush on close failed)
  caller sees SignatureException: upload rejected: signature invalid -> alert security team
*/
