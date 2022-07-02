// Abstract Factory (creational): provide an interface for creating FAMILIES
// of related objects without naming their concrete classes.
//
// The point is consistency: a client that gets all its parts from one
// factory cannot accidentally mix parts from different families. Here a
// "crypto suite" produces a hasher and a message authenticator; a release
// manifest must use a matching pair, and switching the whole application from
// SHA-2 to SHA-3 is a one-line change of the factory.
// JDK examples: javax.xml.parsers.DocumentBuilderFactory and
// javax.xml.transform.TransformerFactory hand out a whole family of parser
// objects from whichever implementation is configured.
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class AbstractFactoryDemo {
    // Product interfaces
    interface Hasher {
        String name();
        byte[] hash(byte[] data);
    }

    interface Authenticator {
        String name();
        byte[] tag(byte[] key, byte[] data);
    }

    // The abstract factory: one method per product in the family
    interface CryptoSuite {
        Hasher createHasher();
        Authenticator createAuthenticator();
    }

    // Concrete factories, one per family
    static final class Sha2Suite implements CryptoSuite {
        public Hasher createHasher() { return new JcaHasher("SHA-256"); }
        public Authenticator createAuthenticator() { return new JcaAuthenticator("HmacSHA256"); }
    }

    static final class Sha3Suite implements CryptoSuite {
        public Hasher createHasher() { return new JcaHasher("SHA3-256"); }
        public Authenticator createAuthenticator() { return new JcaAuthenticator("HmacSHA3-256"); }
    }

    // Concrete products (both families happen to be backed by the JCA)
    record JcaHasher(String name) implements Hasher {
        public byte[] hash(byte[] data) {
            try {
                return MessageDigest.getInstance(name).digest(data);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    record JcaAuthenticator(String name) implements Authenticator {
        public byte[] tag(byte[] key, byte[] data) {
            try {
                Mac mac = Mac.getInstance(name);
                mac.init(new SecretKeySpec(key, name));
                return mac.doFinal(data);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    // The client: it never mentions an algorithm name
    static final class ReleaseSigner {
        private final Hasher hasher;
        private final Authenticator authenticator;

        ReleaseSigner(CryptoSuite suite) {
            this.hasher = suite.createHasher();
            this.authenticator = suite.createAuthenticator();
        }

        String manifest(String artifact, byte[] content, byte[] key) {
            HexFormat hex = HexFormat.of();
            String digest = hex.formatHex(hasher.hash(content));
            String line = artifact + " " + hasher.name() + "=" + digest;
            String tag = hex.formatHex(authenticator.tag(key, line.getBytes(StandardCharsets.UTF_8)));
            // shorten for display; real manifests keep the full values
            return artifact + "\n  " + hasher.name() + "   " + digest.substring(0, 16) + "...\n  "
                    + authenticator.name() + " " + tag.substring(0, 16) + "...";
        }
    }

    public static void main(String[] args) {
        byte[] content = "release 1.4.2 contents".getBytes(StandardCharsets.UTF_8);
        byte[] key = "demo-signing-key-not-for-production".getBytes(StandardCharsets.UTF_8);

        for (CryptoSuite suite : new CryptoSuite[] {new Sha2Suite(), new Sha3Suite()}) {
            System.out.println("== " + suite.getClass().getSimpleName());
            System.out.println(new ReleaseSigner(suite).manifest("app-1.4.2.jar", content, key));
        }
    }
}

/* Output:
== Sha2Suite
app-1.4.2.jar
  SHA-256   22220c10fb3e49e6...
  HmacSHA256 8cbb089ccb5a977b...
== Sha3Suite
app-1.4.2.jar
  SHA3-256   0c2700d74471407b...
  HmacSHA3-256 5907fc84f6a00967...
*/
