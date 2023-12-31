// ResourceBundle holds locale-specific resources, usually UI messages.
// getBundle("Messages", locale) searches candidate bundles from most to least
// specific, e.g. for ko_KR: Messages_ko_KR, Messages_ko, then Messages (base),
// and chains them as parents, so a key missing in Messages_ko is looked up in
// Messages. Bundles can be classes (ListResourceBundle) or .properties files
// (PropertyResourceBundle, read as UTF-8 since Java 9).
// By default the search also tries the JVM's default locale before the base
// bundle, which makes results depend on the machine. The no-fallback Control
// used below turns that off. Pair bundles with MessageFormat for placeholders,
// and give it the same locale so numbers are formatted consistently.
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.TreeSet;

public class ResourceBundleExample {
    static final ResourceBundle.Control NO_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT);

    // Class-based bundles: base name ResourceBundleExample$Alerts
    public static class Alerts extends ListResourceBundle {
        protected Object[][] getContents() {
            return new Object[][] {
                    {"title", "Security alert"},
                    {"failedLogins", "{0,number,integer} failed logins for {1}"},
                    {"footer", "Do not reply to this message."},
            };
        }
    }

    public static class Alerts_ko extends ListResourceBundle {
        protected Object[][] getContents() {
            return new Object[][] {
                    {"title", "보안 경고"},
                    {"failedLogins", "{1} 계정 로그인 실패 {0,number,integer}회"},
                    // no "footer": falls back to the base bundle
            };
        }
    }

    public static class Alerts_de extends ListResourceBundle {
        protected Object[][] getContents() {
            return new Object[][] {
                    {"failedLogins", "{0,number,integer} fehlgeschlagene Anmeldungen für {1}"},
            };
        }
    }

    // Escape non-ASCII characters so the output does not depend on the console encoding
    static String ascii(String s) {
        StringBuilder sb = new StringBuilder();
        s.chars().forEach(c -> sb.append(c < 128 ? String.valueOf((char) c) : String.format("\\u%04x", c)));
        return sb.toString();
    }

    public static void main(String[] args) throws IOException {
        for (Locale locale : new Locale[] {Locale.US, Locale.forLanguageTag("ko-KR"), Locale.GERMANY, Locale.JAPAN}) {
            ResourceBundle rb = ResourceBundle.getBundle(Alerts.class.getName(), locale, NO_FALLBACK);
            String message = new MessageFormat(rb.getString("failedLogins"), locale).format(new Object[] {1234, "alice"});
            System.out.println(locale.toLanguageTag() + " -> " + rb.getClass().getSimpleName());
            System.out.println("    title:   " + ascii(rb.getString("title")));
            System.out.println("    message: " + ascii(message));
            System.out.println("    footer:  " + rb.getString("footer"));
        }

        try {
            ResourceBundle.getBundle(Alerts.class.getName(), Locale.US, NO_FALLBACK).getString("subject");
        } catch (MissingResourceException e) {
            System.out.println("missing key -> MissingResourceException (key " + e.getKey() + ")");
        }

        // Properties-file bundles, loaded from a temp directory through a class loader
        Path dir = Files.createTempDirectory("bundles");
        Path base = Files.writeString(dir.resolve("ui.properties"), "login=Sign in\nlogout=Sign out\n");
        Path fr = Files.writeString(dir.resolve("ui_fr.properties"), "login=Se connecter\n", StandardCharsets.UTF_8);
        try (URLClassLoader loader = new URLClassLoader(new URL[] {dir.toUri().toURL()}, null)) {
            ResourceBundle ui = ResourceBundle.getBundle("ui", Locale.CANADA_FRENCH, loader, NO_FALLBACK);
            System.out.println("fr-CA ui bundle locale = '" + ui.getLocale() + "', login = " + ui.getString("login")
                    + ", logout = " + ui.getString("logout"));
            System.out.println("keys: " + new TreeSet<>(ui.keySet()));
        } finally {
            Files.delete(base);
            Files.delete(fr);
            Files.delete(dir);
        }
    }
}

/* Output:
en-US -> Alerts
    title:   Security alert
    message: 1,234 failed logins for alice
    footer:  Do not reply to this message.
ko-KR -> Alerts_ko
    title:   \ubcf4\uc548 \uacbd\uace0
    message: alice \uacc4\uc815 \ub85c\uadf8\uc778 \uc2e4\ud328 1,234\ud68c
    footer:  Do not reply to this message.
de-DE -> Alerts_de
    title:   Security alert
    message: 1.234 fehlgeschlagene Anmeldungen f\u00fcr alice
    footer:  Do not reply to this message.
ja-JP -> Alerts
    title:   Security alert
    message: 1,234 failed logins for alice
    footer:  Do not reply to this message.
missing key -> MissingResourceException (key subject)
fr-CA ui bundle locale = 'fr', login = Se connecter, logout = Sign out
keys: [login, logout]
*/
