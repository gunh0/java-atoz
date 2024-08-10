// RSA signatures: RSASSA-PSS vs PKCS#1 v1.5
//
// A signature proves who produced a message, to anyone holding the public
// key (unlike a MAC, which needs the shared secret and cannot prove
// authorship to a third party). RSA signatures come in two paddings.
// PKCS#1 v1.5 ("SHA256withRSA") is deterministic and still everywhere
// (X.509, JWT RS256); it is not broken when verified strictly, but lenient
// parsers of its padding produced forgeries (Bleichenbacher 2006, BERserk).
// PSS ("RSASSA-PSS", JWT PS256) adds a random salt, has a security proof, and
// is the choice for new designs; TLS 1.3 requires it for RSA handshake
// signatures. In Java, PSS takes an explicit PSSParameterSpec: hash, MGF1
// hash and salt length must match on both sides.
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Arrays;

public class RsaPss {
    static final PSSParameterSpec PSS = new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);

    public static void main(String[] args) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(3072);
        KeyPair kp = kpg.generateKeyPair();
        byte[] doc = "release v1.4.2, sha256=9f86d081...".getBytes(StandardCharsets.US_ASCII);

        // PKCS#1 v1.5: same key + same message -> same signature
        byte[] v1 = sign("SHA256withRSA", null, kp, doc);
        byte[] v2 = sign("SHA256withRSA", null, kp, doc);
        System.out.println("SHA256withRSA: " + v1.length + "-byte signature, deterministic: " + Arrays.equals(v1, v2)
                + ", verifies: " + verify("SHA256withRSA", null, kp, doc, v1));

        // PSS: randomized salt -> a different signature every time, both valid
        byte[] p1 = sign("RSASSA-PSS", PSS, kp, doc);
        byte[] p2 = sign("RSASSA-PSS", PSS, kp, doc);
        System.out.println("RSASSA-PSS:    " + p1.length + "-byte signature, deterministic: " + Arrays.equals(p1, p2)
                + ", both verify: " + (verify("RSASSA-PSS", PSS, kp, doc, p1) && verify("RSASSA-PSS", PSS, kp, doc, p2)));

        // Any change to the message or signature fails
        byte[] changed = doc.clone();
        changed[8] = '5';
        System.out.println("changed message verifies: " + verify("RSASSA-PSS", PSS, kp, changed, p1));
        byte[] badSig = p1.clone();
        badSig[100] ^= 1;
        System.out.println("changed signature verifies: " + verify("RSASSA-PSS", PSS, kp, doc, badSig));

        // Parameters are part of the scheme: a verifier with another salt length rejects
        PSSParameterSpec otherSalt = new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 20, 1);
        System.out.println("verify with salt length 20 instead of 32: " + verify("RSASSA-PSS", otherSalt, kp, doc, p1));

        // And the two paddings are not interchangeable
        System.out.println("PSS signature checked as PKCS#1 v1.5: " + verify("SHA256withRSA", null, kp, doc, p1));
    }

    static byte[] sign(String alg, PSSParameterSpec spec, KeyPair kp, byte[] data) throws Exception {
        Signature s = Signature.getInstance(alg);
        if (spec != null) s.setParameter(spec);
        s.initSign(kp.getPrivate());
        s.update(data);
        return s.sign();
    }

    static boolean verify(String alg, PSSParameterSpec spec, KeyPair kp, byte[] data, byte[] sig) throws Exception {
        Signature s = Signature.getInstance(alg);
        if (spec != null) s.setParameter(spec);
        s.initVerify(kp.getPublic());
        s.update(data);
        try {
            return s.verify(sig);
        } catch (SignatureException e) {
            return false; // malformed signature: treat exactly like an invalid one
        }
    }
}

/* Output:
SHA256withRSA: 384-byte signature, deterministic: true, verifies: true
RSASSA-PSS:    384-byte signature, deterministic: false, both verify: true
changed message verifies: false
changed signature verifies: false
verify with salt length 20 instead of 32: false
PSS signature checked as PKCS#1 v1.5: false
*/
