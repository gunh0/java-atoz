// java.util.Properties is a string-to-string map with a simple text format:
//   key=value, key: value or key value; # and ! start comments;
//   a trailing backslash continues a line; Unicode escapes are allowed.
//  - load(Reader) / store(Writer, comment) read and write that format
//    (prefer Reader/Writer with an explicit charset over the InputStream
//    versions, which assume ISO-8859-1)
//  - new Properties(defaults) gives a fallback layer: getProperty() looks
//    there when a key is missing, but size() and keySet() don't see it
//  - getProperty(key, default) for optional keys, and validate the rest:
//    every value is a String until you parse it
// store() writes a timestamp comment, so this example prints the keys only.
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.TreeSet;

public class PropertiesExample {
    public static void main(String[] args) throws IOException {
        Properties defaults = new Properties();
        defaults.setProperty("server.port", "8080");
        defaults.setProperty("tls.enabled", "true");
        defaults.setProperty("log.level", "INFO");

        String file = """
                # service configuration
                server.port = 9443
                ! the bind address
                server.host: 127.0.0.1
                allowed.origins = https://app.example.com,\\
                                  https://admin.example.com
                greeting = caf\\u00e9 open
                db.password=
                """;

        Properties config = new Properties(defaults);
        config.load(new StringReader(file));

        System.out.println("own keys:        " + new TreeSet<>(config.stringPropertyNames().stream()
                .filter(config::containsKey).toList()));
        System.out.println("all names:       " + new TreeSet<>(config.stringPropertyNames()));
        System.out.println("server.port      = " + config.getProperty("server.port") + " (file overrides default)");
        System.out.println("tls.enabled      = " + config.getProperty("tls.enabled") + " (from defaults)");
        System.out.println("allowed.origins  = " + config.getProperty("allowed.origins"));
        System.out.println("greeting is 'cafe' + U+00E9: " + config.getProperty("greeting").equals("café open"));
        System.out.println("timeout (absent) = " + config.getProperty("timeout.seconds", "30"));

        // Values are strings: parse and validate them explicitly
        int port = Integer.parseInt(config.getProperty("server.port"));
        boolean tls = Boolean.parseBoolean(config.getProperty("tls.enabled"));
        String password = config.getProperty("db.password");
        System.out.println("port valid: " + (port > 0 && port < 65536) + ", tls: " + tls
                + ", db.password empty: " + password.isEmpty());

        // Round trip through a real file in a temp directory
        Path dir = Files.createTempDirectory("props");
        Path saved = dir.resolve("app.properties");
        try {
            config.setProperty("log.level", "DEBUG");
            try (var writer = Files.newBufferedWriter(saved, StandardCharsets.UTF_8)) {
                config.store(writer, "written by PropertiesExample");
            }
            Properties reloaded = new Properties();
            try (var reader = Files.newBufferedReader(saved, StandardCharsets.UTF_8)) {
                reloaded.load(reader);
            }
            System.out.println("reloaded keys:   " + new TreeSet<>(reloaded.stringPropertyNames())
                    + " (defaults are not stored: no tls.enabled)");
            System.out.println("reloaded log.level = " + reloaded.getProperty("log.level"));

            // store() escapes what the format needs, e.g. ':' '=' and leading spaces
            Properties tricky = new Properties();
            tricky.setProperty("url", "jdbc:postgresql://db:5432/app?ssl=true");
            StringWriter sw = new StringWriter();
            tricky.store(sw, null);
            sw.toString().lines().filter(l -> !l.startsWith("#")).forEach(l -> System.out.println("stored line: " + l));
        } finally {
            Files.deleteIfExists(saved);
            Files.delete(dir);
        }
    }
}

/* Output:
own keys:        [allowed.origins, db.password, greeting, server.host, server.port]
all names:       [allowed.origins, db.password, greeting, log.level, server.host, server.port, tls.enabled]
server.port      = 9443 (file overrides default)
tls.enabled      = true (from defaults)
allowed.origins  = https://app.example.com,https://admin.example.com
greeting is 'cafe' + U+00E9: true
timeout (absent) = 30
port valid: true, tls: true, db.password empty: true
reloaded keys:   [allowed.origins, db.password, greeting, log.level, server.host, server.port] (defaults are not stored: no tls.enabled)
reloaded log.level = DEBUG
stored line: url=jdbc\:postgresql\://db\:5432/app?ssl\=true
*/
