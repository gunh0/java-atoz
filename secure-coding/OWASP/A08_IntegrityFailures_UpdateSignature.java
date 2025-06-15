// OWASP Top 10 2021: A08:2021 Software and Data Integrity Failures, unsigned updates (CWE-494, CWE-345)
// https://owasp.org/Top10/A08_2021-Software_and_Data_Integrity_Failures/
//
// An auto-updater that downloads "app.jar" and "app.jar.sha256" from the
// same server and checks one against the other only detects corruption.
// Whoever can replace the file (a compromised mirror or CDN, a hijacked
// bucket, a network attacker on plain HTTP) replaces the checksum too.
// Integrity needs a key the attacker does not have: the vendor signs a
// small manifest (name, version, SHA-256 of the artifact) with a private
// key kept offline, and the client verifies it with a public key it ships
// with (pinned), then checks the artifact against the signed hash. The
// signed version number also stops a rollback to an older, genuinely
// signed but vulnerable release. Keys are generated in-process here
// (Ed25519); nothing random is printed.
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

public class A08_IntegrityFailures_UpdateSignature {
    // What the update server returns: artifact, checksum file, signed manifest
    record Download(byte[] blob, String checksumFile, String manifest, byte[] signature) { }

    static String sha256(byte[] data) throws GeneralSecurityException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    static byte[] sign(PrivateKey key, String manifest) throws GeneralSecurityException {
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(key);
        s.update(manifest.getBytes(StandardCharsets.UTF_8));
        return s.sign();
    }

    static Download release(PrivateKey key, String version, byte[] blob) throws GeneralSecurityException {
        String manifest = "name=app\nversion=" + version + "\nsha256=" + sha256(blob) + "\n";
        return new Download(blob, sha256(blob), manifest, sign(key, manifest));
    }

    // Noncompliant: the checksum comes from the same place as the file
    static String installNoncompliant(Download d) throws GeneralSecurityException {
        return sha256(d.blob()).equals(d.checksumFile()) ? "INSTALLED" : "rejected: checksum mismatch";
    }

    // Compliant: verify the manifest signature with the pinned key, then the hash, then the version
    static String installCompliant(Download d, PublicKey pinned, String installedVersion) throws GeneralSecurityException {
        Signature s = Signature.getInstance("Ed25519");
        s.initVerify(pinned);
        s.update(d.manifest().getBytes(StandardCharsets.UTF_8));
        if (!s.verify(d.signature())) return "rejected: manifest signature invalid";
        Map<String, String> m = new LinkedHashMap<>();
        d.manifest().lines().forEach(l -> m.put(l.substring(0, l.indexOf('=')), l.substring(l.indexOf('=') + 1)));
        // Constant-time comparison of the signed hash and the downloaded blob's hash
        if (!MessageDigest.isEqual(m.get("sha256").getBytes(StandardCharsets.US_ASCII),
                sha256(d.blob()).getBytes(StandardCharsets.US_ASCII))) {
            return "rejected: artifact does not match signed hash";
        }
        if (compareVersions(m.get("version"), installedVersion) <= 0) {
            return "rejected: version " + m.get("version") + " is not newer than " + installedVersion;
        }
        return "INSTALLED " + m.get("version");
    }

    static int compareVersions(String a, String b) {
        return Arrays.compare(Arrays.stream(a.split("\\.")).mapToInt(Integer::parseInt).toArray(),
                Arrays.stream(b.split("\\.")).mapToInt(Integer::parseInt).toArray());
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair vendor = kpg.generateKeyPair();   // private half stays with the vendor
        KeyPair attacker = kpg.generateKeyPair();
        PublicKey pinned = vendor.getPublic();    // shipped inside the client
        String installed = "2.0.5"; // currently running

        byte[] old = "app 2.0.0 (has a known remote-code-execution bug)".getBytes(StandardCharsets.UTF_8);
        byte[] genuine = "app 2.1.0".getBytes(StandardCharsets.UTF_8);
        byte[] trojan = "app 2.1.0 + backdoor".getBytes(StandardCharsets.UTF_8);

        Download good = release(vendor.getPrivate(), "2.1.0", genuine);
        Map<String, Download> cases = new LinkedHashMap<>();
        cases.put("genuine 2.1.0", good);
        // The mirror swaps the file and regenerates the checksum, keeps the old manifest
        cases.put("trojan, checksum replaced", new Download(trojan, sha256(trojan), good.manifest(), good.signature()));
        // ...or edits the manifest's hash as well
        String forgedManifest = good.manifest().replace(sha256(genuine), sha256(trojan));
        cases.put("trojan, manifest edited", new Download(trojan, sha256(trojan), forgedManifest, good.signature()));
        // ...or signs everything with its own key
        cases.put("trojan, signed by attacker", release(attacker.getPrivate(), "2.1.0", trojan));
        // ...or replays an old release that the vendor really signed
        cases.put("rollback to signed 2.0.0", release(vendor.getPrivate(), "2.0.0", old));

        for (var e : cases.entrySet()) {
            System.out.println(e.getKey());
            System.out.println("  noncompliant (checksum file): " + installNoncompliant(e.getValue()));
            System.out.println("  compliant (pinned signature): " + installCompliant(e.getValue(), pinned, installed));
        }
    }
}

/* Output:
genuine 2.1.0
  noncompliant (checksum file): INSTALLED
  compliant (pinned signature): INSTALLED 2.1.0
trojan, checksum replaced
  noncompliant (checksum file): INSTALLED
  compliant (pinned signature): rejected: artifact does not match signed hash
trojan, manifest edited
  noncompliant (checksum file): INSTALLED
  compliant (pinned signature): rejected: manifest signature invalid
trojan, signed by attacker
  noncompliant (checksum file): INSTALLED
  compliant (pinned signature): rejected: manifest signature invalid
rollback to signed 2.0.0
  noncompliant (checksum file): INSTALLED
  compliant (pinned signature): rejected: version 2.0.0 is not newer than 2.0.5
*/
