// DateTimeFormatter turns temporal objects into text and back. It is
// immutable and thread-safe, unlike the old SimpleDateFormat, so a single
// static instance can be shared.
//  - predefined ISO_* constants match what toString()/parse() already use
//  - ofPattern("dd/MMM/yyyy:HH:mm:ss Z", Locale.US): pin the Locale whenever
//    month or day names appear, or output changes with the machine's settings
//  - parsing is strict about the pattern; failures throw DateTimeParseException
//    with the index of the offending character
//  - pattern letters are case-sensitive: MM is month, mm is minute; yyyy is
//    year-of-era, YYYY is week-based year (a classic year-end bug)
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.Locale;

public class FormattingExample {
    // Apache / nginx access-log timestamp: 27/Jul/2023:14:03:07 +0900
    static final DateTimeFormatter ACCESS_LOG =
            DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", Locale.US);

    public static void main(String[] args) {
        LocalDateTime ldt = LocalDateTime.of(2023, 7, 27, 14, 3, 7);

        System.out.println("ISO_LOCAL_DATE_TIME: " + ldt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        System.out.println("ISO_DATE:            " + ldt.format(DateTimeFormatter.ISO_DATE));
        System.out.println("BASIC_ISO_DATE:      " + ldt.format(DateTimeFormatter.BASIC_ISO_DATE));
        System.out.println("custom:              " + ldt.format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")));
        System.out.println("US, with names:      " + ldt.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy h:mm a", Locale.US)));
        System.out.println("German, with names:  " + ldt.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)));
        System.out.println("localized MEDIUM:    " + ldt.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.US)));

        // Round trip of an access-log timestamp
        String logLine = "10.0.0.7 - - [27/Jul/2023:14:03:07 +0900] \"GET /admin HTTP/1.1\" 403";
        String stamp = logLine.substring(logLine.indexOf('[') + 1, logLine.indexOf(']'));
        OffsetDateTime when = OffsetDateTime.parse(stamp, ACCESS_LOG);
        System.out.println("parsed log time: " + when + " = " + when.withOffsetSameInstant(ZoneOffset.UTC) + " UTC");
        System.out.println("formatted back:  " + when.format(ACCESS_LOG));

        // Strict parsing: errors carry the position
        for (String input : new String[] {"2023-07-27", "2023-7-27", "2023-02-30"}) {
            try {
                System.out.println("parse " + input + " -> " + LocalDate.parse(input));
            } catch (DateTimeParseException e) {
                System.out.println("parse " + input + " -> error at index " + e.getErrorIndex() + ": " + e.getMessage());
            }
        }

        // yyyy vs YYYY: 2023-12-31 is a Sunday that belongs to week 1 of 2024 (US rules)
        LocalDate yearEnd = LocalDate.of(2023, 12, 31);
        System.out.println("yyyy: " + yearEnd.format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)));
        System.out.println("YYYY: " + yearEnd.format(DateTimeFormatter.ofPattern("YYYY-MM-dd", Locale.US)));
    }
}

/* Output:
ISO_LOCAL_DATE_TIME: 2023-07-27T14:03:07
ISO_DATE:            2023-07-27
BASIC_ISO_DATE:      20230727
custom:              2023.07.27 14:03
US, with names:      Thursday, July 27, 2023 2:03 PM
German, with names:  Donnerstag, 27. Juli 2023
localized MEDIUM:    Jul 27, 2023
parsed log time: 2023-07-27T14:03:07+09:00 = 2023-07-27T05:03:07Z UTC
formatted back:  27/Jul/2023:14:03:07 +0900
parse 2023-07-27 -> 2023-07-27
parse 2023-7-27 -> error at index 5: Text '2023-7-27' could not be parsed at index 5
parse 2023-02-30 -> error at index 0: Text '2023-02-30' could not be parsed: Invalid date 'FEBRUARY 30'
yyyy: 2023-12-31
YYYY: 2024-12-31
*/
