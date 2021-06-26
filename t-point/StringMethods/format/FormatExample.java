// String.format(format, args...) builds a string from a template, using the
// same format specifiers as printf: %s (string), %d (integer), %f (floating
// point), %x (hex), %c (char), %b (boolean), %% (a literal %), %n (the
// platform line separator). Flags and widths go between % and the
// conversion: %-10s left-aligns in 10 columns, %05d pads with zeros, %.2f
// keeps two decimals, %,d adds grouping separators.
// Number formatting depends on the locale, so pass one explicitly when the
// output must not change between machines.
import java.util.Locale;

public class FormatExample {
    public static void main(String[] args) {
        String name = "Alice";
        int age = 30;
        double balance = 1234567.891;

        System.out.println(String.format("Name: %s, Age: %d", name, age));
        System.out.println(String.format(Locale.ROOT, "Balance: %.2f", balance));
        System.out.println(String.format(Locale.ROOT, "Grouped: %,.2f", balance));
        System.out.println(String.format(Locale.GERMANY, "German:  %,.2f", balance));

        // Width, alignment and padding: a small table
        System.out.println(String.format("|%-8s|%6s|", "item", "qty"));
        System.out.println(String.format("|%-8s|%6d|", "apple", 3));
        System.out.println(String.format("|%-8s|%06d|", "pear", 12));

        // Other conversions
        System.out.println(String.format("hex: %x, HEX: %X, octal: %o", 255, 255, 8));
        System.out.println(String.format("char: %c, boolean: %b, percent: 50%%", 'J', true));
        System.out.println(String.format(Locale.ROOT, "scientific: %.3e", 0.000123));

        // Argument index: reuse an argument with n$
        System.out.println(String.format("%1$s-%1$s-%2$s", "ha", "!"));

        // Java 15+: the same thing as an instance method
        System.out.println("%s is %d years old".formatted(name, age));

        // A mismatched argument fails at run time, not at compile time
        try {
            String.format("%d", "not a number");
        } catch (java.util.IllegalFormatException e) {
            System.out.println("bad argument: " + e.getClass().getSimpleName());
        }
    }
}

/* Output:
Name: Alice, Age: 30
Balance: 1234567.89
Grouped: 1,234,567.89
German:  1.234.567,89
|item    |   qty|
|apple   |     3|
|pear    |000012|
hex: ff, HEX: FF, octal: 10
char: J, boolean: true, percent: 50%
scientific: 1.230e-04
ha-ha-!
Alice is 30 years old
bad argument: IllegalFormatConversionException
*/
