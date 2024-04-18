// Listing the algorithms a JDK supports
//
// Security.getAlgorithms(serviceType) returns the standard names offered by
// all installed providers for one engine type, e.g. "MessageDigest" or
// "Cipher". It is the quickest way to answer audit questions such as "is
// MD5 still available?" (yes: removing it would break old file formats, so
// it is up to you not to use it) or "which signature schemes can I pick?".
// The set is unordered and grows over releases, so this program prints a
// sorted, filtered subset and classifies each name. Cipher entries are base
// algorithms only; modes and paddings ("AES/GCM/NoPadding") are separate
// attributes of each service.
import java.security.Security;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class AlgorithmListing {
    public static void main(String[] args) {
        Set<String> digests = new TreeSet<>(Security.getAlgorithms("MessageDigest"));
        System.out.println("MessageDigest:");
        for (String d : List.of("MD2", "MD5", "SHA-1", "SHA-224", "SHA-256", "SHA-384", "SHA-512",
                "SHA-512/256", "SHA3-256", "SHA3-512")) {
            System.out.printf("  %-12s %-9s %s%n", d, digests.contains(d) ? "available" : "missing", verdict(d));
        }

        // Mac names: the HMAC family, sorted
        Set<String> macs = new TreeSet<>();
        for (String m : Security.getAlgorithms("Mac")) {
            if (m.matches("HMACSHA(256|384|512|3-256)")) macs.add(m);
        }
        System.out.println("Mac (HMAC-SHA2/3 subset): " + macs);

        // Signature names for the schemes used in this section
        Set<String> sigs = new TreeSet<>();
        for (String s : Security.getAlgorithms("Signature")) {
            if (s.equals("SHA256WITHRSA") || s.equals("RSASSA-PSS") || s.equals("SHA256WITHECDSA")
                    || s.equals("SHA256WITHECDSAINP1363FORMAT") || s.equals("ED25519")) sigs.add(s);
        }
        System.out.println("Signature (subset): " + sigs);

        // Key agreement
        System.out.println("KeyAgreement: " + new TreeSet<>(subset(Security.getAlgorithms("KeyAgreement"), "ECDH", "X25519", "X448", "DIFFIEHELLMAN")));

        // Note the case: getAlgorithms returns names upper-cased; getInstance
        // is case-insensitive, so "hmacsha256" works as well as "HmacSHA256".
        System.out.println("names are upper-cased: " + macs.contains("HMACSHA256"));
    }

    static Set<String> subset(Set<String> all, String... wanted) {
        Set<String> out = new TreeSet<>();
        for (String w : wanted) if (all.contains(w)) out.add(w);
        return out;
    }

    static String verdict(String digest) {
        return switch (digest) {
            case "MD2", "MD5" -> "broken (collisions), legacy checksums only";
            case "SHA-1" -> "broken for signatures (chosen-prefix collisions)";
            default -> "ok";
        };
    }
}

/* Output:
MessageDigest:
  MD2          available broken (collisions), legacy checksums only
  MD5          available broken (collisions), legacy checksums only
  SHA-1        available broken for signatures (chosen-prefix collisions)
  SHA-224      available ok
  SHA-256      available ok
  SHA-384      available ok
  SHA-512      available ok
  SHA-512/256  available ok
  SHA3-256     available ok
  SHA3-512     available ok
Mac (HMAC-SHA2/3 subset): [HMACSHA256, HMACSHA3-256, HMACSHA384, HMACSHA512]
Signature (subset): [ED25519, RSASSA-PSS, SHA256WITHECDSA, SHA256WITHECDSAINP1363FORMAT, SHA256WITHRSA]
KeyAgreement: [DIFFIEHELLMAN, ECDH, X25519, X448]
names are upper-cased: true
*/
