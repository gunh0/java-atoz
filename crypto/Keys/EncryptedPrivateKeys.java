// output-jdk: 24 (prints the default PBES2 algorithm, which JDK 26 changed)
// Encrypted PKCS#8 private keys: EncryptedPrivateKeyInfo and PBES2
//
// "-----BEGIN ENCRYPTED PRIVATE KEY-----" is PKCS#8 EncryptedPrivateKeyInfo
// (RFC 5208/5958): an AlgorithmIdentifier saying how the key was encrypted,
// followed by the encrypted PrivateKeyInfo bytes. The algorithm should be
// PBES2 (RFC 8018): PBKDF2 with HMAC-SHA256 and a real iteration count
// derives an AES-256 key, and AES-CBC encrypts the key. The older PBES1
// schemes (PBEWithMD5AndDES and friends: a 56-bit DES key, an 8-byte salt)
// are still parsed everywhere, and old tooling still emits them, so check
// what your files actually contain. In Java: encrypt the PKCS#8 bytes with a
// PBE Cipher, wrap result and parameters in EncryptedPrivateKeyInfo,
// getEncoded() gives the DER for the PEM body; reading back,
// getAlgName() and getAlgParameters() describe the scheme and
// getKeySpec(password key) returns the PKCS8EncodedKeySpec for a KeyFactory.
// A wrong password surfaces as an InvalidKeyException from getKeySpec (CBC
// padding or DER parsing fails); there is no MAC, so do not treat "it
// decrypted" as proof of integrity. The OpenSSL "traditional" PEM
// with "Proc-Type: 4,ENCRYPTED" headers is a different format the JDK does
// not read.
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

public class EncryptedPrivateKeys {
    static final SecureRandom RNG = new SecureRandom();

    static SecretKey passwordKey(String pbeAlg, char[] password) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password);
        try {
            return SecretKeyFactory.getInstance(pbeAlg).generateSecret(spec);
        } finally {
            spec.clearPassword();
        }
    }

    static byte[] encryptPbes2(PrivateKey key, char[] password, int iterations) throws Exception {
        String alg = "PBEWithHmacSHA256AndAES_256";
        byte[] salt = new byte[16];
        byte[] iv = new byte[16];
        RNG.nextBytes(salt);
        RNG.nextBytes(iv);
        Cipher c = Cipher.getInstance(alg);
        c.init(Cipher.ENCRYPT_MODE, passwordKey(alg, password), new PBEParameterSpec(salt, iterations, new IvParameterSpec(iv)));
        byte[] encrypted = c.doFinal(key.getEncoded());
        // The cipher's parameters are named after the cipher; EncryptedPrivateKeyInfo
        // needs them under the standard name "PBES2" to write the right OID
        AlgorithmParameters pbes2 = AlgorithmParameters.getInstance("PBES2");
        pbes2.init(c.getParameters().getEncoded());
        return new EncryptedPrivateKeyInfo(pbes2, encrypted).getEncoded();
    }

    static byte[] encryptPbes1(PrivateKey key, char[] password) throws Exception {
        String alg = "PBEWithMD5AndDES"; // legacy, for comparison only
        byte[] salt = new byte[8];
        RNG.nextBytes(salt);
        Cipher c = Cipher.getInstance(alg);
        c.init(Cipher.ENCRYPT_MODE, passwordKey(alg, password), new PBEParameterSpec(salt, 2048));
        return new EncryptedPrivateKeyInfo(c.getParameters(), c.doFinal(key.getEncoded())).getEncoded();
    }

    static PrivateKey decrypt(byte[] der, char[] password) throws Exception {
        EncryptedPrivateKeyInfo info = new EncryptedPrivateKeyInfo(der);
        PKCS8EncodedKeySpec spec = info.getKeySpec(passwordKey(info.getAlgName(), password));
        return KeyFactory.getInstance(spec.getAlgorithm()).generatePrivate(spec);
    }

    static String pem(byte[] der) {
        return "-----BEGIN ENCRYPTED PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
                + "\n-----END ENCRYPTED PRIVATE KEY-----\n";
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = kpg.generateKeyPair();
        char[] password = "correct horse battery staple".toCharArray();

        byte[] der = encryptPbes2(kp.getPrivate(), password, 600_000);
        String pem = pem(der);
        System.out.println(pem.lines().findFirst().orElseThrow());
        System.out.println("DER bytes: " + der.length + ", PEM lines: " + pem.lines().count());

        // What a reader (or an auditor) can see without the password
        EncryptedPrivateKeyInfo info = new EncryptedPrivateKeyInfo(der);
        PBEParameterSpec params = info.getAlgParameters().getParameterSpec(PBEParameterSpec.class);
        System.out.println("scheme:     " + info.getAlgParameters().getAlgorithm() + " = " + info.getAlgName());
        System.out.println("iterations: " + params.getIterationCount() + ", salt bytes: " + params.getSalt().length);

        // Right password: the key comes back and still signs
        PrivateKey restored = decrypt(der, password);
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(restored);
        s.update("hello".getBytes(StandardCharsets.UTF_8));
        byte[] sig = s.sign();
        s.initVerify(kp.getPublic());
        s.update("hello".getBytes(StandardCharsets.UTF_8));
        System.out.println("decrypted key: " + restored.getAlgorithm() + ", signature verifies: " + s.verify(sig));

        // Wrong password
        try {
            decrypt(der, "Tr0ub4dor&3".toCharArray());
            System.out.println("wrong password accepted?!");
        } catch (GeneralSecurityException e) {
            System.out.println("wrong password rejected: " + (e instanceof InvalidKeyException));
        }

        // A legacy PBES1 file reads just as easily, which is the problem
        byte[] legacy = encryptPbes1(kp.getPrivate(), password);
        EncryptedPrivateKeyInfo legacyInfo = new EncryptedPrivateKeyInfo(legacy);
        PBEParameterSpec lp = legacyInfo.getAlgParameters().getParameterSpec(PBEParameterSpec.class);
        System.out.println("legacy scheme: " + legacyInfo.getAlgName() + ", salt bytes: " + lp.getSalt().length
                + ", DES key bits: 56, readable: " + decrypt(legacy, password).getAlgorithm());
    }
}

/* Output:
-----BEGIN ENCRYPTED PRIVATE KEY-----
DER bytes: 186, PEM lines: 6
scheme:     PBES2 = PBEWithHmacSHA256AndAES_256
iterations: 600000, salt bytes: 16
decrypted key: EC, signature verifies: true
wrong password rejected: true
legacy scheme: PBEWithMD5AndDES, salt bytes: 8, DES key bits: 56, readable: EC
*/
