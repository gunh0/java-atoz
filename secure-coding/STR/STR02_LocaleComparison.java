// STR02-J. Specify an appropriate locale when comparing locale-dependent data
// https://wiki.sei.cmu.edu/confluence/display/java/STR02-J.+Specify+an+appropriate+locale+when+comparing+locale-dependent+data
//
// String.toUpperCase() and toLowerCase() without arguments use the JVM's
// default locale, and case mapping is locale-dependent. In Turkish (and
// Azerbaijani) the lowercase of 'I' is the dotless U+0131 and the uppercase
// of 'i' is the dotted U+0130. So a check like
// tag.toUpperCase().equals("SCRIPT") behaves differently depending on where
// the server runs, and a blocklist can be bypassed or a protocol keyword
// misread. For identifiers, keywords and protocol data use Locale.ROOT (or
// Locale.ENGLISH); keep the user's locale for text shown to that user.
import java.util.Locale;

public class STR02_LocaleComparison {
    public static void main(String[] args) {
        Locale original = Locale.getDefault();
        try {
            for (Locale locale : new Locale[] {Locale.US, Locale.forLanguageTag("tr-TR")}) {
                Locale.setDefault(locale); // simulate a server configured for this locale
                System.out.println("default locale " + locale + ":");
                for (String tag : new String[] {"script", "SCRIPT", "title"}) {
                    System.out.printf("  %-6s noncompliant blocked=%-5s compliant blocked=%-5s"
                                    + " default upper=%s%n",
                            tag, isBlockedNoncompliant(tag), isBlockedCompliant(tag), escape(tag.toUpperCase()));
                }
                System.out.println("  \"TITLE\".toLowerCase() = " + escape("TITLE".toLowerCase())
                        + ", with Locale.ROOT = " + "TITLE".toLowerCase(Locale.ROOT));
            }
        } finally {
            Locale.setDefault(original);
        }
    }

    // Noncompliant: depends on the default locale
    static boolean isBlockedNoncompliant(String tag) {
        return tag.toUpperCase().equals("SCRIPT");
    }

    // Compliant: locale-independent case mapping for protocol data
    static boolean isBlockedCompliant(String tag) {
        return tag.toUpperCase(Locale.ROOT).equals("SCRIPT");
    }

    // Show non-ASCII characters as \\uXXXX
    static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            sb.append(c < 128 ? String.valueOf(c) : String.format("\\u%04X", (int) c));
        }
        return sb.toString();
    }
}

/* Output:
default locale en_US:
  script noncompliant blocked=true  compliant blocked=true  default upper=SCRIPT
  SCRIPT noncompliant blocked=true  compliant blocked=true  default upper=SCRIPT
  title  noncompliant blocked=false compliant blocked=false default upper=TITLE
  "TITLE".toLowerCase() = title, with Locale.ROOT = title
default locale tr_TR:
  script noncompliant blocked=false compliant blocked=true  default upper=SCR\u0130PT
  SCRIPT noncompliant blocked=true  compliant blocked=true  default upper=SCRIPT
  title  noncompliant blocked=false compliant blocked=false default upper=T\u0130TLE
  "TITLE".toLowerCase() = t\u0131tle, with Locale.ROOT = title
*/
