// RSA encryption: OAEP vs PKCS#1 v1.5, and the OAEP parameter trap
//
// PKCS#1 v1.5 encryption padding (00 02 <random nonzero bytes> 00 <message>)
// is randomized but has a checkable structure, and a server that reveals
// whether the padding of a decrypted ciphertext was valid is an oracle:
// Bleichenbacher's 1998 attack, rediscovered against TLS servers as ROBOT
// in 2017. OAEP (PKCS#1 v2) is provably secure and is the choice for RSA
// encryption. But beware SunJCE's transformation string:
// "RSA/ECB/OAEPWithSHA-256AndMGF1Padding" means SHA-256 for the label hash
// and SHA-1 for MGF1, while most other libraries use SHA-256 for both, so
// the two sides fail to interoperate. Always pass an explicit
// OAEPParameterSpec. RSA encryption only fits short data (a key): wrap a
// symmetric key with it, or better, use a KEM or ECDH-based hybrid scheme.
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.MGF1ParameterSpec;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

public class RsaOaep {
    static final OAEPParameterSpec OAEP_SHA256 = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    public static void main(String[] args) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        byte[] secret = "a 32-byte AES key would go here!".getBytes(StandardCharsets.US_ASCII);

        // Explicit parameters on both sides: works
        Cipher enc = Cipher.getInstance("RSA/ECB/OAEPPadding");
        enc.init(Cipher.ENCRYPT_MODE, kp.getPublic(), OAEP_SHA256);
        byte[] ct = enc.doFinal(secret);
        Cipher dec = Cipher.getInstance("RSA/ECB/OAEPPadding");
        dec.init(Cipher.DECRYPT_MODE, kp.getPrivate(), OAEP_SHA256);
        System.out.println("OAEP SHA-256/MGF1-SHA-256: " + new String(dec.doFinal(ct), StandardCharsets.US_ASCII));
        System.out.println("ciphertext length = modulus length: " + ct.length + " bytes");

        // The trap: the receiver relies on the transformation string's defaults
        Cipher implicit = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        implicit.init(Cipher.DECRYPT_MODE, kp.getPrivate());
        OAEPParameterSpec used = implicit.getParameters().getParameterSpec(OAEPParameterSpec.class);
        System.out.println("transformation string implies: digest " + used.getDigestAlgorithm()
                + ", MGF1 with " + ((MGF1ParameterSpec) used.getMGFParameters()).getDigestAlgorithm());
        try {
            implicit.doFinal(ct);
        } catch (BadPaddingException e) {
            System.out.println("decrypting with the implicit defaults: " + e.getClass().getSimpleName());
        }

        // Capacity: k - 2*hLen - 2 bytes for OAEP, k - 11 for PKCS#1 v1.5
        int k = 256;
        System.out.println("max plaintext, RSA-2048: OAEP-SHA256 " + (k - 2 * 32 - 2) + " bytes, PKCS#1 v1.5 " + (k - 11) + " bytes");
        try {
            enc.init(Cipher.ENCRYPT_MODE, kp.getPublic(), OAEP_SHA256);
            enc.doFinal(new byte[191]);
        } catch (IllegalBlockSizeException e) {
            System.out.println("191 bytes with OAEP-SHA256: " + e.getClass().getSimpleName());
        }

        // PKCS#1 v1.5 still works (and is needed for old formats), but a
        // decryptor whose failures can be told apart is a Bleichenbacher oracle.
        Cipher v15 = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        v15.init(Cipher.ENCRYPT_MODE, kp.getPublic());
        byte[] legacy = v15.doFinal(secret);
        v15.init(Cipher.DECRYPT_MODE, kp.getPrivate());
        System.out.println("PKCS#1 v1.5 round trip: " + new String(v15.doFinal(legacy), StandardCharsets.US_ASCII).equals(
                new String(secret, StandardCharsets.US_ASCII)));
    }
}

/* Output:
OAEP SHA-256/MGF1-SHA-256: a 32-byte AES key would go here!
ciphertext length = modulus length: 256 bytes
transformation string implies: digest SHA-256, MGF1 with SHA-1
decrypting with the implicit defaults: BadPaddingException
max plaintext, RSA-2048: OAEP-SHA256 190 bytes, PKCS#1 v1.5 245 bytes
191 bytes with OAEP-SHA256: IllegalBlockSizeException
PKCS#1 v1.5 round trip: true
*/
