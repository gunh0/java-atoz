// System properties (java -Dkey=value, System.getProperty) and environment
// variables (System.getenv) are the two built-in ways to configure a JVM
// program from outside. Reading them safely means:
//  - always handle "not set": getProperty(key, default), getenv() -> null
//  - parse with a fallback: Integer.getInteger / Boolean.getBoolean read
//    system properties (not env vars!) and never throw on bad input
//  - getenv() is read-only; set a child's environment via ProcessBuilder
//  - never log the whole environment or all properties: they often carry
//    secrets (tokens, passwords); log names, or mask the values
// Machine-specific values (paths, user names, versions) are not printed here.
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

public class SystemPropertiesExample {
    static final Pattern SECRET_NAME = Pattern.compile("(?i).*(pass|secret|token|key|credential).*");

    static String mask(String name, String value) {
        if (value == null) return "<unset>";
        if (SECRET_NAME.matcher(name).matches()) return "****(" + value.length() + " chars)";
        return value;
    }

    static int intSetting(String property, String env, int fallback) {
        // -D property wins over the environment variable, which wins over the default
        String raw = Optional.ofNullable(System.getProperty(property))
                .or(() -> Optional.ofNullable(System.getenv(env)))
                .orElse(null);
        if (raw == null) return fallback;
        try {
            return Integer.parseInt(raw.strip());
        } catch (NumberFormatException e) {
            System.out.println("  ignoring invalid " + property + "=" + raw);
            return fallback;
        }
    }

    public static void main(String[] args) {
        // Standard properties that every JVM defines
        System.out.println("line.separator matches lineSeparator(): "
                + System.getProperty("line.separator").equals(System.lineSeparator()));
        System.out.println("file.separator is one char: " + (System.getProperty("file.separator").length() == 1));
        System.out.println("java.version set: " + (System.getProperty("java.version") != null));

        // Missing properties: null, or the default you pass
        System.out.println("app.mode: " + System.getProperty("app.mode") + " / with default: "
                + System.getProperty("app.mode", "development"));

        // Programmatic set (normally: java -Dapp.mode=prod ...)
        System.setProperty("app.mode", "production");
        System.setProperty("app.workers", "8");
        System.setProperty("app.debug", "TRUE");
        System.setProperty("app.retries", "three");
        System.out.println("app.mode: " + System.getProperty("app.mode"));
        System.out.println("Integer.getInteger(app.workers, 4): " + Integer.getInteger("app.workers", 4));
        System.out.println("Integer.getInteger(app.retries, 3): " + Integer.getInteger("app.retries", 3) + " (bad value -> default)");
        System.out.println("Boolean.getBoolean(app.debug): " + Boolean.getBoolean("app.debug") + " (case-insensitive \"true\")");
        System.out.println("Boolean.getBoolean(app.verbose): " + Boolean.getBoolean("app.verbose"));
        System.out.println("intSetting(app.workers): " + intSetting("app.workers", "APP_WORKERS_X", 1));
        System.out.println("intSetting(app.retries): " + intSetting("app.retries", "APP_RETRIES_X", 3));
        System.clearProperty("app.mode");
        System.out.println("after clearProperty: " + System.getProperty("app.mode"));

        // Environment variables: absent ones are null
        String name = "JAVA_ATOZ_SURELY_UNSET_VAR";
        System.out.println(name + " = " + System.getenv(name));
        System.out.println("via Optional: " + Optional.ofNullable(System.getenv(name)).orElse("fallback"));

        // The environment map cannot be modified from Java
        try {
            System.getenv().put("APP_MODE", "x");
        } catch (UnsupportedOperationException e) {
            System.out.println("getenv().put -> " + e.getClass().getSimpleName());
        }

        // Logging configuration safely: names in order, secret values masked
        Map<String, String> config = new TreeMap<>(Map.of(
                "APP_MODE", "production",
                "DB_PASSWORD", "hunter2hunter2",
                "API_TOKEN", "tok_abcdef0123456789",
                "AWS_ACCESS_KEY_ID", "AKIAEXAMPLE0000",
                "LOG_LEVEL", "info"));
        config.forEach((k, v) -> System.out.println("  " + k + "=" + mask(k, v)));
    }
}

/* Output:
line.separator matches lineSeparator(): true
file.separator is one char: true
java.version set: true
app.mode: null / with default: development
app.mode: production
Integer.getInteger(app.workers, 4): 8
Integer.getInteger(app.retries, 3): 3 (bad value -> default)
Boolean.getBoolean(app.debug): true (case-insensitive "true")
Boolean.getBoolean(app.verbose): false
intSetting(app.workers): 8
  ignoring invalid app.retries=three
intSetting(app.retries): 3
after clearProperty: null
JAVA_ATOZ_SURELY_UNSET_VAR = null
via Optional: fallback
getenv().put -> UnsupportedOperationException
  API_TOKEN=****(20 chars)
  APP_MODE=production
  AWS_ACCESS_KEY_ID=****(15 chars)
  DB_PASSWORD=****(14 chars)
  LOG_LEVEL=info
*/
