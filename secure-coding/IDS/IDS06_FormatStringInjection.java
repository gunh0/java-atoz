// IDS06-J. Exclude unsanitized user input from format strings
// https://wiki.sei.cmu.edu/confluence/display/java/IDS06-J.+Exclude+unsanitized+user+input+from+format+strings
//
// Java's Formatter is memory safe, unlike C's printf, but a format string
// still decides which arguments get printed and how. If user input becomes
// part of the format string, the user can add conversions such as %1$tm or
// %1$te that print the other arguments, including ones that were never
// meant to be shown, or malformed ones that make the call throw. Keep the
// format string a constant and pass user input only as an argument (%s),
// where it is printed verbatim.
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.IllegalFormatException;
import java.util.Locale;

public class IDS06_FormatStringInjection {
    // The card's expiry date is the secret being guessed; the issue date is a harmless hint
    private static final Calendar EXPIRY = new GregorianCalendar(2026, Calendar.NOVEMBER, 23);
    private static final Calendar ISSUED = new GregorianCalendar(2022, Calendar.MARCH, 1);

    public static void main(String[] args) {
        String[] guesses = {"10", "%1$tm/%1$te/%1$tY", "%999$s"};
        for (String guess : guesses) {
            System.out.println("input " + guess);
            System.out.println("  noncompliant: " + checkNoncompliant(guess));
            System.out.println("  compliant:    " + checkCompliant(guess));
        }
    }

    // Noncompliant: the user's text is concatenated into the format string,
    // so it can refer to argument 1, the secret expiry date
    static String checkNoncompliant(String month) {
        if (month.equals(String.valueOf(EXPIRY.get(Calendar.MONTH) + 1))) {
            return "matched";
        }
        try {
            return String.format(Locale.US, month + " did not match! HINT: the card was issued in %2$tB", EXPIRY, ISSUED);
        } catch (IllegalFormatException e) {
            return e.getClass().getSimpleName();
        }
    }

    // Compliant: the format string is a constant; the input is only an argument
    static String checkCompliant(String month) {
        if (month.equals(String.valueOf(EXPIRY.get(Calendar.MONTH) + 1))) {
            return "matched";
        }
        return String.format(Locale.US, "%s did not match! HINT: the card was issued in %tB", month, ISSUED);
    }
}

/* Output:
input 10
  noncompliant: 10 did not match! HINT: the card was issued in March
  compliant:    10 did not match! HINT: the card was issued in March
input %1$tm/%1$te/%1$tY
  noncompliant: 11/23/2026 did not match! HINT: the card was issued in March
  compliant:    %1$tm/%1$te/%1$tY did not match! HINT: the card was issued in March
input %999$s
  noncompliant: MissingFormatArgumentException
  compliant:    %999$s did not match! HINT: the card was issued in March
*/
