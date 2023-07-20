// ERR04-J. Do not complete abruptly from a finally block
// https://wiki.sei.cmu.edu/confluence/display/java/ERR04-J.+Do+not+complete+abruptly+from+a+finally+block
//
// A finally block that ends with return, break, continue or throw replaces
// whatever the try block was doing: a pending exception is discarded and a
// pending return value is overwritten. A failed signature check, an
// interrupted transfer or an access-denied error can thus be turned into a
// normal "success" result without any trace. Let finally blocks only clean
// up (close, unlock, reset) and fall off their end normally.
public class ERR04_FinallyAbrupt {
    public static void main(String[] args) {
        String[] tokens = {"valid-token", "forged-token"};
        for (String token : tokens) {
            System.out.println(token + ": noncompliant " + authorizeNoncompliant(token)
                    + " | compliant " + authorizeCompliant(token));
        }
    }

    static void verify(String token) {
        if (!token.startsWith("valid")) {
            throw new SecurityException("bad signature");
        }
    }

    // Noncompliant: the return in finally swallows the SecurityException
    @SuppressWarnings("finally")
    static String authorizeNoncompliant(String token) {
        String result = "granted";
        try {
            verify(token); // throws for a forged token...
        } finally {
            audit(token);
            return result; // ...but this return discards the exception
        }
    }

    // Compliant: finally only cleans up; the exception propagates
    static String authorizeCompliant(String token) {
        try {
            try {
                verify(token);
                return "granted";
            } finally {
                audit(token);
            }
        } catch (SecurityException e) {
            return "denied (" + e.getMessage() + ")";
        }
    }

    static void audit(String token) {
        // e.g. write an audit record; must not change the outcome
    }
}

/* Output:
valid-token: noncompliant granted | compliant granted
forged-token: noncompliant granted | compliant denied (bad signature)
*/
