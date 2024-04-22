// MessageFormat builds messages from a pattern with numbered placeholders:
//   {0}                   the argument's toString (numbers/dates get formatted)
//   {1,number,integer}    a sub-format: number (integer, percent, currency,
//                         or a DecimalFormat pattern), date, time, choice
// An argument may appear several times, in any order, which is what
// translations need. A single quote starts literal text, so write '' for an
// apostrophe and '{' to print a brace: "it's" in a pattern silently loses
// its quote and everything after it is taken literally.
// Pass a Locale to the constructor (the static format() uses the default
// locale), and note that date sub-formats use the default time zone unless
// you set one on them.
import java.text.DateFormat;
import java.text.Format;
import java.text.MessageFormat;
import java.text.ParseException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class MessageFormatExample {
    public static void main(String[] args) throws ParseException {
        MessageFormat mf = new MessageFormat(
                "User {0} has {1,number,integer} failed logins ({2,number,percent} of attempts).", Locale.US);
        System.out.println(mf.format(new Object[] {"alice", 1234, 0.25}));

        // Custom number pattern and reusing an argument
        MessageFormat price = new MessageFormat("{0}: {1,number,#,##0.00} USD, {0} is in stock", Locale.US);
        System.out.println(price.format(new Object[] {"Keyboard", 1299.5}));

        // Translators can reorder arguments without touching the code
        Object[] loginArgs = {3, "bob"};
        System.out.println(new MessageFormat("{0,number,integer} failed logins for {1}", Locale.US).format(loginArgs));
        System.out.println(new MessageFormat("Account {1}: {0,number,integer} failed logins", Locale.US).format(loginArgs));

        // Quoting rules
        System.out.println(new MessageFormat("It''s {0}''s turn", Locale.US).format(new Object[] {"Carol"}));
        System.out.println(new MessageFormat("It's {0}'s turn", Locale.US).format(new Object[] {"Carol"}));
        System.out.println(new MessageFormat("Use '{0}' to insert {0}", Locale.US).format(new Object[] {"a value"}));

        // Date sub-format: pin the time zone, otherwise the output depends on the machine
        MessageFormat audit = new MessageFormat("Login at {0,date,yyyy-MM-dd} {0,time,HH:mm} UTC", Locale.US);
        Date when = Date.from(Instant.parse("2024-04-22T09:30:00Z"));
        System.out.println(fixZone(audit).format(new Object[] {when}));

        // Missing arguments are left as the placeholder text
        System.out.println(new MessageFormat("{0} and {1}", Locale.US).format(new Object[] {"only one"}));

        // parse() goes the other way
        MessageFormat log = new MessageFormat("{0} -> {1}", Locale.US);
        Object[] parts = log.parse("10.0.0.1 -> 10.0.0.2");
        System.out.println("parsed: " + Arrays.toString(parts));
    }

    // Every {0,date} / {0,time} element has its own DateFormat; set UTC on each one
    static MessageFormat fixZone(MessageFormat mf) {
        Format[] formats = mf.getFormats();
        for (Format f : formats) {
            if (f instanceof DateFormat df) {
                df.setTimeZone(TimeZone.getTimeZone("UTC"));
            }
        }
        mf.setFormats(formats);
        return mf;
    }
}

/* Output:
User alice has 1,234 failed logins (25% of attempts).
Keyboard: 1,299.50 USD, Keyboard is in stock
3 failed logins for bob
Account bob: 3 failed logins
It's Carol's turn
Its {0}s turn
Use {0} to insert a value
Login at 2024-04-22 09:30 UTC
only one and {1}
parsed: [10.0.0.1, 10.0.0.2]
*/
