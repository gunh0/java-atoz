// OBJ10-J. Do not use public static nonfinal fields
// https://wiki.sei.cmu.edu/confluence/display/java/OBJ10-J.+Do+not+use+public+static+nonfinal+fields
//
// A public static field that is not final is a global variable every piece
// of code in the JVM can overwrite, including libraries and plugins the
// application does not fully trust. If it holds security configuration (a
// hash algorithm, a minimum key size, a "debug" switch), one assignment
// anywhere silently weakens every later use, and there is no way to
// validate or audit the change. Make such fields private static final (for
// constants) or private with a validating accessor if they must change.
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

public class OBJ10_PublicStaticFields {
    public static void main(String[] args) throws NoSuchAlgorithmException {
        System.out.println("noncompliant before plugin: " + PasswordStore.hashNoncompliant("hunter2").length() / 2 + "-byte "
                + PasswordStore.algorithm);
        UntrustedPlugin.init();
        System.out.println("noncompliant after plugin:  " + PasswordStore.hashNoncompliant("hunter2").length() / 2 + "-byte "
                + PasswordStore.algorithm);

        System.out.println("compliant after plugin:     " + PasswordStore.hashCompliant("hunter2").length() / 2 + "-byte "
                + PasswordStore.algorithm());
        try {
            PasswordStore.setAlgorithm("MD5");
        } catch (IllegalArgumentException e) {
            System.out.println("compliant setter: " + e.getMessage());
        }
    }
}

class PasswordStore {
    // Noncompliant: anyone can assign PasswordStore.algorithm
    public static String algorithm = "SHA-256";

    // Compliant: private, validated on change
    private static final Set<String> ALLOWED = Set.of("SHA-256", "SHA-512");
    private static volatile String compliantAlgorithm = "SHA-256";

    static String algorithm() {
        return compliantAlgorithm;
    }

    static void setAlgorithm(String name) {
        if (!ALLOWED.contains(name)) {
            throw new IllegalArgumentException("algorithm not allowed: " + name);
        }
        compliantAlgorithm = name;
    }

    static String hashNoncompliant(String password) throws NoSuchAlgorithmException {
        return hash(algorithm, password);
    }

    static String hashCompliant(String password) throws NoSuchAlgorithmException {
        return hash(compliantAlgorithm, password);
    }

    private static String hash(String alg, String password) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance(alg).digest(password.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}

// Third-party code that "helps" with compatibility
class UntrustedPlugin {
    static void init() {
        PasswordStore.algorithm = "MD5";
    }
}

/* Output:
noncompliant before plugin: 32-byte SHA-256
noncompliant after plugin:  16-byte MD5
compliant after plugin:     32-byte SHA-256
compliant setter: algorithm not allowed: MD5
*/
