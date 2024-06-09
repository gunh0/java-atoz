// "template".formatted(args...) (Java 15) is the instance-method twin of
// String.format(template, args...): same Formatter syntax, same result. It
// reads left to right and chains well after a text block or a constant.
// The one thing it lacks is a Locale parameter: formatted() always uses the
// default FORMAT locale, while String.format(Locale, ...) takes one explicitly.
// So for numbers that must not change between machines (logs, files, IDs)
// prefer String.format(Locale.ROOT, ...), and keep formatted() for text that
// has no locale-sensitive conversions (%s, %d without grouping, %x, ...).
import java.util.Locale;

public class FormattedExample {
    static final String ROW = "| %-10s | %5d | %-8s |";

    public static void main(String[] args) {
        // Same output, two spellings
        String a = String.format("user=%s id=%d", "alice", 42);
        String b = "user=%s id=%d".formatted("alice", 42);
        System.out.println(a);
        System.out.println(b);
        System.out.println("equal: " + a.equals(b));

        // formatted() chains naturally on a constant template
        System.out.println(ROW.formatted("alice", 3, "admin"));
        System.out.println(ROW.formatted("bob", 120, "viewer"));

        // ... and on a text block
        String json = """
                {"user": "%s", "failedLogins": %d}""".formatted("carol", 5);
        System.out.println(json);

        // Locale: formatted() follows the default locale, format() can pin one.
        // Pin the default here so the demo is reproducible, then compare.
        Locale saved = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.Category.FORMAT, Locale.GERMANY);
            double amount = 1234567.891;
            System.out.println("formatted (default de-DE): " + "%,.2f".formatted(amount));
            System.out.println("format(Locale.ROOT):       " + String.format(Locale.ROOT, "%,.2f", amount));
            System.out.println("format(Locale.US):         " + String.format(Locale.US, "%,.2f", amount));
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, saved);
        }

        // Both throw the same unchecked exceptions on a bad template or arguments
        try {
            "%d".formatted("not a number");
        } catch (java.util.IllegalFormatException e) {
            System.out.println("bad argument -> " + e.getClass().getSimpleName());
        }
        try {
            "%s and %s".formatted("only one");
        } catch (java.util.IllegalFormatException e) {
            System.out.println("missing argument -> " + e.getClass().getSimpleName());
        }

        // Concatenation is still fine (and clearer) for trivial cases
        String name = "dave";
        System.out.println("hello " + name + " vs " + "hello %s".formatted(name));
    }
}

/* Output:
user=alice id=42
user=alice id=42
equal: true
| alice      |     3 | admin    |
| bob        |   120 | viewer   |
{"user": "carol", "failedLogins": 5}
formatted (default de-DE): 1.234.567,89
format(Locale.ROOT):       1,234,567.89
format(Locale.US):         1,234,567.89
bad argument -> IllegalFormatConversionException
missing argument -> MissingFormatArgumentException
hello dave vs hello dave
*/
