// A custom exception hierarchy lets callers choose how coarse their handling
// is: catch the base type to handle "anything from this module", or a
// subtype to react to one specific failure.
//  - extend Exception for conditions a caller can reasonably recover from
//    (checked), RuntimeException for programming errors (unchecked)
//  - carry structured data (an error code, the offending field) instead of
//    making callers parse the message
//  - keep the hierarchy shallow; catch clauses must list subtypes before
//    their supertypes, or javac rejects the unreachable catch
import java.util.List;

public class CustomHierarchyExample {

    enum ErrorCode { VALIDATION, NOT_FOUND, CONFLICT }

    // Base checked exception of the "account service" module
    static class AccountException extends Exception {
        private final ErrorCode code;

        AccountException(ErrorCode code, String message) {
            super(message);
            this.code = code;
        }

        ErrorCode code() { return code; }
    }

    static class ValidationException extends AccountException {
        private final String field;

        ValidationException(String field, String message) {
            super(ErrorCode.VALIDATION, message);
            this.field = field;
        }

        String field() { return field; }
    }

    static class AccountNotFoundException extends AccountException {
        AccountNotFoundException(String id) {
            super(ErrorCode.NOT_FOUND, "no account " + id);
        }
    }

    static class DuplicateAccountException extends AccountException {
        DuplicateAccountException(String id) {
            super(ErrorCode.CONFLICT, "account " + id + " already exists");
        }
    }

    // Unchecked: a bug in the caller, not something to recover from
    static class AccountStateError extends RuntimeException {
        AccountStateError(String message) { super(message); }
    }

    static final List<String> EXISTING = List.of("alice", "bob");

    static void create(String id) throws AccountException {
        if (id == null || id.isBlank()) {
            throw new ValidationException("id", "id must not be blank");
        }
        if (!id.matches("[a-z]{3,16}")) {
            throw new ValidationException("id", "id must be 3-16 lowercase letters");
        }
        if (EXISTING.contains(id)) {
            throw new DuplicateAccountException(id);
        }
        System.out.println("  created " + id);
    }

    static void close(String id) throws AccountException {
        if (!EXISTING.contains(id)) {
            throw new AccountNotFoundException(id);
        }
        if (id.equals("bob")) {
            throw new AccountStateError("close() called twice for " + id);
        }
        System.out.println("  closed " + id);
    }

    public static void main(String[] args) {
        System.out.println("fine-grained handling:");
        for (String id : new String[] {"carol", "", "X1", "alice"}) {
            try {
                create(id);
            } catch (ValidationException e) {        // most specific first
                System.out.println("  400 field=" + e.field() + ": " + e.getMessage());
            } catch (DuplicateAccountException e) {
                System.out.println("  409 " + e.getMessage());
            } catch (AccountException e) {           // anything else from the module
                System.out.println("  500 " + e.code());
            }
        }

        System.out.println("coarse handling by base type and error code:");
        for (String id : new String[] {"alice", "dave", "bob"}) {
            try {
                close(id);
            } catch (AccountException e) {
                System.out.println("  " + e.getClass().getSimpleName() + " code=" + e.code() + ": " + e.getMessage());
            } catch (AccountStateError e) {
                System.out.println("  bug: " + e.getMessage());
            }
        }

        System.out.println("hierarchy:");
        for (Class<?> c = ValidationException.class; c != Object.class; c = c.getSuperclass()) {
            System.out.println("  " + c.getSimpleName());
        }
    }
}

/* Output:
fine-grained handling:
  created carol
  400 field=id: id must not be blank
  400 field=id: id must be 3-16 lowercase letters
  409 account alice already exists
coarse handling by base type and error code:
  closed alice
  AccountNotFoundException code=NOT_FOUND: no account dave
  bug: close() called twice for bob
hierarchy:
  ValidationException
  AccountException
  Exception
  Throwable
*/
