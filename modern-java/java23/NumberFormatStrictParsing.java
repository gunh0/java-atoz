// NumberFormat.setStrict: strict number parsing (Java 23)
//
// NumberFormat.parse has always been lenient: it reads the longest prefix
// that looks like a number and silently ignores the rest. "1234abc" parses
// as 1234, "1.2.3" as 1.2, "1e3" as 1, and an integer format turns "12.5"
// into 12. For input validation that is dangerous: a value an attacker
// controls passes the check and means something other than what was typed.
// Java 23 adds isStrict()/setStrict(boolean) to NumberFormat (implemented
// by DecimalFormat, CompactNumberFormat and ChoiceFormat). In strict mode
// parse(String) throws ParseException unless the whole string is a
// well-formed number, including correct grouping separators.
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.List;
import java.util.Locale;

public class NumberFormatStrictParsing {
    public static void main(String[] args) {
        NumberFormat lenient = NumberFormat.getInstance(Locale.US);
        NumberFormat strict = NumberFormat.getInstance(Locale.US);
        strict.setStrict(true);
        System.out.println("default isStrict: " + lenient.isStrict());

        System.out.printf("%-12s %-10s %s%n", "input", "lenient", "strict");
        for (String s : List.of("1,234.50", "1234abc", "1.2.3", "1e3", "12,34", "1,234,", "12  ")) {
            System.out.printf("%-12s %-10s %s%n", "\"" + s + "\"", parse(lenient, s), parse(strict, s));
        }

        // Integer formats: lenient truncates the fraction, strict refuses it
        NumberFormat lenientInt = NumberFormat.getIntegerInstance(Locale.US);
        NumberFormat strictInt = NumberFormat.getIntegerInstance(Locale.US);
        strictInt.setStrict(true);
        System.out.println("integer \"12.5\": lenient " + parse(lenientInt, "12.5") + ", strict " + parse(strictInt, "12.5"));

        // Strict grouping follows the format: with grouping off, "1,234" is not a number
        NumberFormat plain = NumberFormat.getInstance(Locale.US);
        plain.setGroupingUsed(false);
        plain.setStrict(true);
        System.out.println("grouping off: \"1234\" " + parse(plain, "1234") + ", \"1,234\" " + parse(plain, "1,234"));

        // With a ParsePosition, strict mode returns null and records where parsing failed
        ParsePosition pos = new ParsePosition(0);
        Number n = strictInt.parse("12x", pos);
        System.out.println("parse(\"12x\", pos): " + n + ", error index " + pos.getErrorIndex());
    }

    static String parse(NumberFormat format, String s) {
        try {
            return String.valueOf(format.parse(s));
        } catch (ParseException e) {
            return "rejected";
        }
    }
}

/* Output:
default isStrict: false
input        lenient    strict
"1,234.50"   1234.5     1234.5
"1234abc"    1234       rejected
"1.2.3"      1.2        rejected
"1e3"        1          rejected
"12,34"      1234       rejected
"1,234,"     1234       rejected
"12  "       12         rejected
integer "12.5": lenient 12, strict rejected
grouping off: "1234" 1234, "1,234" rejected
parse("12x", pos): null, error index 2
*/
