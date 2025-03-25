// Fixed patterns such as "dd.MM.yyyy" are right for logs and file names, but
// for people a date should look the way their locale writes it. java.time
// takes the formats from the Unicode CLDR data shipped with the JDK:
//  - ofLocalizedDate / ofLocalizedTime / ofLocalizedDateTime(FormatStyle)
//    with SHORT, MEDIUM, LONG or FULL, plus withLocale(locale)
//  - ofLocalizedPattern("yMMMd") (Java 19): name the fields you want, the
//    locale decides order, separators and spelling
//  - localizedBy(locale) also honours Unicode extensions in the locale, e.g.
//    "-u-ca-japanese" switches the calendar
//  - TextStyle names for months and days; WeekFields for the first day of
//    the week and week numbers, which differ between countries
// Never pin tests to the JVM's default locale, and remember the formats come
// from CLDR: they are data, not API, and may change between releases.
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;

public class LocalizedFormattingExample {
    static final List<Locale> LOCALES = List.of(Locale.US, Locale.UK, Locale.GERMANY, Locale.FRANCE, Locale.KOREA, Locale.JAPAN);

    // U+202F (narrow no-break space) appears in some CLDR time formats; make it visible
    static String visible(String s) {
        return s.replace(" ", "<NNBSP>").replace(" ", "<NBSP>");
    }

    public static void main(String[] args) {
        LocalDate date = LocalDate.of(2025, 3, 25);
        for (FormatStyle style : FormatStyle.values()) {
            System.out.println("-- " + style);
            for (Locale locale : LOCALES) {
                DateTimeFormatter f = DateTimeFormatter.ofLocalizedDate(style).withLocale(locale);
                System.out.printf("  %-6s %s%n", locale, date.format(f));
            }
        }

        System.out.println("-- ofLocalizedPattern(\"yMMMd\")");
        for (Locale locale : LOCALES) {
            System.out.printf("  %-6s %s%n", locale, date.format(DateTimeFormatter.ofLocalizedPattern("yMMMd").withLocale(locale)));
        }

        System.out.println("-- time and the narrow no-break space");
        DateTimeFormatter usTime = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.US);
        String formatted = LocalTime.of(15, 30).format(usTime);
        System.out.println("  en_US SHORT time: " + visible(formatted));
        try {
            LocalTime.parse("3:30 PM", usTime); // typed with an ordinary space
            System.out.println("  parse \"3:30 PM\": ok");
        } catch (DateTimeParseException e) {
            System.out.println("  parse \"3:30 PM\": DateTimeParseException");
        }
        DateTimeFormatter lenient = new DateTimeFormatterBuilder().parseLenient()
                .appendLocalized(null, FormatStyle.SHORT).toFormatter(Locale.US);
        System.out.println("  lenient parse \"3:30 PM\": " + LocalTime.parse("3:30 PM", lenient));

        System.out.println("-- LONG date-time needs a time zone");
        DateTimeFormatter longDateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG).withLocale(Locale.UK);
        LocalDateTime noZone = LocalDateTime.of(2025, 3, 25, 9, 0);
        try {
            noZone.format(longDateTime);
        } catch (DateTimeException e) {
            System.out.println("  LocalDateTime: " + e.getClass().getSimpleName());
        }
        System.out.println("  ZonedDateTime: " + visible(noZone.atZone(ZoneId.of("Europe/London")).format(longDateTime)));

        System.out.println("-- localizedBy honours the calendar extension");
        Locale japaneseCalendar = Locale.forLanguageTag("ja-JP-u-ca-japanese");
        DateTimeFormatter longJa = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG);
        System.out.println("  withLocale:  " + date.format(longJa.withLocale(japaneseCalendar)));
        System.out.println("  localizedBy: " + date.format(longJa.localizedBy(japaneseCalendar)));

        System.out.println("-- parsing localized text");
        DateTimeFormatter mediumUs = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.US);
        System.out.println("  \"Mar 25, 2025\" -> " + LocalDate.parse("Mar 25, 2025", mediumUs));
        DateTimeFormatter germanMonth = new DateTimeFormatterBuilder().parseCaseInsensitive()
                .appendPattern("d. MMMM uuuu").toFormatter(Locale.GERMANY);
        System.out.println("  \"25. MÄRZ 2025\" (case-insensitive, German) -> " + LocalDate.parse("25. MÄRZ 2025", germanMonth));

        System.out.println("-- names and weeks");
        for (Locale locale : List.of(Locale.US, Locale.GERMANY, Locale.KOREA)) {
            WeekFields weeks = WeekFields.of(locale);
            System.out.printf("  %-6s %s / %s / %s, week starts %s, week of year %d%n", locale,
                    Month.MARCH.getDisplayName(TextStyle.FULL, locale),
                    DayOfWeek.TUESDAY.getDisplayName(TextStyle.SHORT, locale),
                    DayOfWeek.TUESDAY.getDisplayName(TextStyle.NARROW, locale),
                    weeks.getFirstDayOfWeek(), LocalDate.of(2025, 1, 5).get(weeks.weekOfWeekBasedYear()));
        }
    }
}

/* Output:
-- FULL
  en_US  Tuesday, March 25, 2025
  en_GB  Tuesday, 25 March 2025
  de_DE  Dienstag, 25. März 2025
  fr_FR  mardi 25 mars 2025
  ko_KR  2025년 3월 25일 화요일
  ja_JP  2025年3月25日火曜日
-- LONG
  en_US  March 25, 2025
  en_GB  25 March 2025
  de_DE  25. März 2025
  fr_FR  25 mars 2025
  ko_KR  2025년 3월 25일
  ja_JP  2025年3月25日
-- MEDIUM
  en_US  Mar 25, 2025
  en_GB  25 Mar 2025
  de_DE  25.03.2025
  fr_FR  25 mars 2025
  ko_KR  2025. 3. 25.
  ja_JP  2025/03/25
-- SHORT
  en_US  3/25/25
  en_GB  25/03/2025
  de_DE  25.03.25
  fr_FR  25/03/2025
  ko_KR  25. 3. 25.
  ja_JP  2025/03/25
-- ofLocalizedPattern("yMMMd")
  en_US  Mar 25, 2025
  en_GB  25 Mar 2025
  de_DE  25. März 2025
  fr_FR  25 mars 2025
  ko_KR  2025년 3월 25일
  ja_JP  2025年3月25日
-- time and the narrow no-break space
  en_US SHORT time: 3:30<NNBSP>PM
  parse "3:30 PM": DateTimeParseException
  lenient parse "3:30 PM": 15:30
-- LONG date-time needs a time zone
  LocalDateTime: DateTimeException
  ZonedDateTime: 25 March 2025, 09:00:00 GMT
-- localizedBy honours the calendar extension
  withLocale:  2025年3月25日
  localizedBy: 令和7年3月25日
-- parsing localized text
  "Mar 25, 2025" -> 2025-03-25
  "25. MÄRZ 2025" (case-insensitive, German) -> 2025-03-25
-- names and weeks
  en_US  March / Tue / T, week starts SUNDAY, week of year 2
  de_DE  März / Di. / D, week starts MONDAY, week of year 1
  ko_KR  3월 / 화 / 화, week starts SUNDAY, week of year 2
*/
