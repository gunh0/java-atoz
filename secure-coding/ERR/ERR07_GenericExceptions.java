// ERR07-J. Do not throw RuntimeException, Exception, or Throwable
// https://wiki.sei.cmu.edu/confluence/display/java/ERR07-J.+Do+not+throw+RuntimeException,+Exception,+or+Throwable
//
// Throwing the generic RuntimeException, Exception or Throwable forces
// callers to catch the generic type to handle your error, and a catch of
// the generic type also catches everything else: NullPointerException,
// ArithmeticException, ClassCastException, the bugs. Those are then
// handled as if they were the expected, harmless condition (here a "bad
// input" that gets a polite 400 while a real defect is hidden). Throw a
// specific existing exception (IllegalArgumentException,
// IllegalStateException...) or your own subclass, and catch exactly that.
import java.util.List;
import java.util.Map;

public class ERR07_GenericExceptions {
    // An optional discount table; the "vip" entry was forgotten in this deployment
    static final Map<String, Integer> DISCOUNTS = Map.of("student", 10);

    public static void main(String[] args) {
        for (String input : List.of("120:student", "abc", "120:vip")) {
            System.out.println(String.format("%-12s noncompliant: %-22s compliant: %s",
                    input, handleNoncompliant(input), handleCompliant(input)));
        }
    }

    // Noncompliant: throws RuntimeException, so callers catch RuntimeException
    static int parseNoncompliant(String input) {
        String[] parts = input.split(":");
        if (parts.length != 2) {
            throw new RuntimeException("expected price:category");
        }
        int discount = DISCOUNTS.get(parts[1]); // NPE when the category is missing: a bug
        return Integer.parseInt(parts[0]) * (100 - discount) / 100;
    }

    static String handleNoncompliant(String input) {
        try {
            return "price " + parseNoncompliant(input);
        } catch (RuntimeException e) { // also swallows the NullPointerException
            return "400 bad request";
        }
    }

    // Compliant: a specific exception for the expected failure
    static int parseCompliant(String input) {
        String[] parts = input.split(":");
        if (parts.length != 2) {
            throw new InvalidOrderException("expected price:category");
        }
        int discount = DISCOUNTS.get(parts[1]);
        return Integer.parseInt(parts[0]) * (100 - discount) / 100;
    }

    static String handleCompliant(String input) {
        try {
            return "price " + parseCompliant(input);
        } catch (InvalidOrderException e) {
            return "400 bad request";
        } catch (NullPointerException e) { // shown here only to report it; normally it would propagate
            return "500, bug surfaced: " + e.getClass().getSimpleName();
        }
    }
}

class InvalidOrderException extends IllegalArgumentException {
    InvalidOrderException(String message) {
        super(message);
    }
}

/* Output:
120:student  noncompliant: price 108              compliant: price 108
abc          noncompliant: 400 bad request        compliant: 400 bad request
120:vip      noncompliant: 400 bad request        compliant: 500, bug surfaced: NullPointerException
*/
