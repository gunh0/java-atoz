// java.util.Formatter is the engine behind String.format and printf. Use it
// directly to append formatted text to any Appendable (StringBuilder, Writer,
// PrintStream) without building intermediate strings.
// Format specifier: %[argument_index$][flags][width][.precision]conversion
//   %2$s   take the second argument       %<s   reuse the previous argument
//   flags: - left-justify, 0 zero-pad, + sign, ( negative in parentheses,
//          , grouping, # alternate form (0x for hex, leading 0 for octal)
// Locale-sensitive conversions use the Formatter's locale, so pass one.
// A class can control its own %s output by implementing Formattable.
// Formatter is Closeable; close it when it wraps a stream or file.
import java.time.LocalDate;
import java.util.FormatFlagsConversionMismatchException;
import java.util.Formattable;
import java.util.FormattableFlags;
import java.util.Formatter;
import java.util.IllegalFormatConversionException;
import java.util.Locale;
import java.util.MissingFormatArgumentException;

public class FormatterExample {
    // A host name that abbreviates itself when the precision is too small
    record Host(String name) implements Formattable {
        @Override
        public void formatTo(Formatter f, int flags, int width, int precision) {
            String s = name;
            if (precision >= 0 && s.length() > precision) {
                s = s.substring(0, Math.max(0, precision - 1)) + "~";
            }
            if ((flags & FormattableFlags.UPPERCASE) != 0) {
                s = s.toUpperCase(Locale.ROOT);
            }
            if (width > s.length()) {
                String pad = " ".repeat(width - s.length());
                s = (flags & FormattableFlags.LEFT_JUSTIFY) != 0 ? s + pad : pad + s;
            }
            f.format("%s", s);
        }
    }

    public static void main(String[] args) {
        StringBuilder sb = new StringBuilder();
        try (Formatter f = new Formatter(sb, Locale.US)) {
            // Argument indexes and reuse
            f.format("%2$s before %1$s; %1$s again; %<S uppercased%n", "first", "second");

            // Number flags
            f.format("[%d] [%+d] [%(d] [%,d] [%08d] [%-8d]%n", 42, 42, -42, 1234567, 42, 42);
            f.format("[%x] [%#x] [%X] [%o] [%#o]%n", 255, 255, 255, 8, 8);
            f.format("[%.3f] [%10.2f] [%-10.2f] [%e] [%.2e]%n", Math.PI, Math.PI, Math.PI, 12345.678, 0.000123);

            // Strings, chars, booleans
            f.format("[%10s] [%-10s] [%.3s] [%c] [%b] [%b]%n", "right", "left", "truncate", 'x', null, "anything");

            // Dates via %t with java.time types (no time zone involved for LocalDate)
            LocalDate d = LocalDate.of(2024, 6, 2);
            f.format("%tF | %<tA, %<tB %<te, %<tY | %<tD%n", d);

            // Formattable controls its own output
            Host host = new Host("build-server-01.internal");
            f.format("[%s] [%.10s] [%-12.8S] [%30s]%n", host, host, host, host);

            System.out.print(f.out());
        }

        // The same Formatter can write straight to System.out
        Formatter console = new Formatter(System.out, Locale.GERMANY);
        console.format("German locale: %,.2f%n", 1234567.891);
        console.flush(); // do not close: that would close System.out

        // Mistakes surface as IllegalFormatException subclasses at run time
        Object[][] bad = {{"%d", "text"}, {"%s %s", "one"}, {"%#d", 1}};
        for (Object[] b : bad) {
            try {
                String.format(Locale.US, (String) b[0], b[1]);
            } catch (IllegalFormatConversionException | MissingFormatArgumentException
                     | FormatFlagsConversionMismatchException e) {
                System.out.println(b[0] + " -> " + e.getClass().getSimpleName());
            }
        }
    }
}

/* Output:
second before first; first again; FIRST uppercased
[42] [+42] [(42)] [1,234,567] [00000042] [42      ]
[ff] [0xff] [FF] [10] [010]
[3.142] [      3.14] [3.14      ] [1.234568e+04] [1.23e-04]
[     right] [left      ] [tru] [x] [false] [true]
2024-06-02 | Sunday, June 2, 2024 | 06/02/24
[build-server-01.internal] [build-ser~] [BUILD-S~    ] [      build-server-01.internal]
German locale: 1.234.567,89
%d -> IllegalFormatConversionException
%s %s -> MissingFormatArgumentException
%#d -> FormatFlagsConversionMismatchException
*/
