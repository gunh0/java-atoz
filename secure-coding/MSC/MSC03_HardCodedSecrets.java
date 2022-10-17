// MSC03-J. Never hard code sensitive information
// https://wiki.sei.cmu.edu/confluence/display/java/MSC03-J.+Never+hard+code+sensitive+information
//
// A password, API key or private key written into the source ends up in the
// compiled .class file as a plain constant-pool string, and from there in
// every JAR, container image, backup and decompiler output. Anyone with the
// binary can read it (javap -v or strings is enough), and rotating it needs a
// new release. Keep secrets out of code: read them at run time from a
// protected source (environment, a file with restricted permissions, a
// secrets manager), and keep them out of logs. This program compiles two
// tiny classes with javax.tools and searches the resulting bytes.
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.tools.ToolProvider;

public class MSC03_HardCodedSecrets {
    static final String NONCOMPLIANT = """
            class DbClient {
                private static final String PASSWORD = "Tr0ub4dor&3";

                String connect() {
                    return "jdbc:postgresql://db/app?password=" + PASSWORD;
                }
            }
            """;

    static final String COMPLIANT = """
            class DbClient {
                private final char[] password;

                DbClient(char[] password) {
                    this.password = password.clone();
                }

                boolean hasPassword() {
                    return password.length > 0;
                }
            }
            """;

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("msc03");
        try {
            System.out.println("noncompliant class file contains the password: "
                    + classContains(dir.resolve("a"), NONCOMPLIANT, "Tr0ub4dor&3"));
            System.out.println("compliant class file contains the password:    "
                    + classContains(dir.resolve("b"), COMPLIANT, "Tr0ub4dor&3"));

            // Compliant: the secret arrives at run time, e.g. from the environment
            // (simulated with a map) or a permission-restricted file
            Map<String, String> environment = Map.of("DB_PASSWORD", "Tr0ub4dor&3");
            char[] fromEnv = environment.get("DB_PASSWORD").toCharArray();
            Path secretFile = Files.writeString(dir.resolve("db.secret"), "Tr0ub4dor&3");
            char[] fromFile = Files.readString(secretFile).toCharArray();
            System.out.println("secret from environment and file match: " + java.util.Arrays.equals(fromEnv, fromFile));
            java.util.Arrays.fill(fromEnv, '\0'); // clear when no longer needed
            java.util.Arrays.fill(fromFile, '\0');
        } finally {
            try (var walk = Files.walk(dir)) {
                for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(p);
                }
            }
        }
    }

    // Compile the source and look for the secret in the raw class file bytes
    static boolean classContains(Path dir, String source, String secret) throws Exception {
        Files.createDirectories(dir);
        Path src = Files.writeString(dir.resolve("DbClient.java"), source);
        int rc = ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", dir.toString(), src.toString());
        if (rc != 0) {
            throw new IllegalStateException("compilation failed");
        }
        byte[] classBytes = Files.readAllBytes(dir.resolve("DbClient.class"));
        String asLatin1 = new String(classBytes, StandardCharsets.ISO_8859_1); // byte-for-byte view
        return asLatin1.contains(secret);
    }
}

/* Output:
noncompliant class file contains the password: true
compliant class file contains the password:    false
secret from environment and file match: true
*/
