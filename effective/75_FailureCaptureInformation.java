// Effective Java, Item 75: Include failure-capture information in detail
// messages.
//
// Often a stack trace and its detail message are all an engineer gets from
// a production failure, and the bug may not be reproducible. So the message
// of an exception should contain the values of every parameter and field
// that contributed to the failure - for an index error, the index and the
// bounds. Put that information in the constructor (and in accessors), so
// every throw site gets it for free instead of hand-crafting strings. One
// security caveat: stack traces end up in logs and bug reports, so never put
// passwords, keys or full card numbers in a detail message.
public class FailureCaptureInformation {
    // Captures the failure in the constructor; accessors expose the values
    static class RangeException extends IndexOutOfBoundsException {
        private final int lower, upper, index;

        RangeException(int lower, int upper, int index) {
            super(String.format("index %d not in [%d, %d)", index, lower, upper));
            this.lower = lower;
            this.upper = upper;
            this.index = index;
        }

        int index() {
            return index;
        }
    }

    static int[] window = {10, 20, 30};

    static int getVague(int i) {
        if (i < 0 || i >= window.length) {
            throw new IndexOutOfBoundsException("bad index"); // what index? what bounds?
        }
        return window[i];
    }

    static int get(int i) {
        if (i < 0 || i >= window.length) {
            throw new RangeException(0, window.length, i);
        }
        return window[i];
    }

    static void loginVague(String user, String password) {
        throw new SecurityException("login failed for " + user + " with password " + password); // leaks it
    }

    static void login(String user, String password) {
        throw new SecurityException("login failed for user " + user + " (bad credentials)"); // no secret
    }

    public static void main(String[] args) {
        try {
            getVague(5);
        } catch (IndexOutOfBoundsException e) {
            System.out.println("vague:   " + e.getMessage());
        }
        try {
            get(5);
        } catch (RangeException e) {
            System.out.println("capture: " + e.getMessage() + " (index() = " + e.index() + ")");
        }

        try {
            loginVague("ann", "hunter2");
        } catch (SecurityException e) {
            System.out.println("leaky:   " + e.getMessage());
        }
        try {
            login("ann", "hunter2");
        } catch (SecurityException e) {
            System.out.println("safe:    " + e.getMessage());
        }
    }
}

/* Output:
vague:   bad index
capture: index 5 not in [0, 3) (index() = 5)
leaky:   login failed for ann with password hunter2
safe:    login failed for user ann (bad credentials)
*/
