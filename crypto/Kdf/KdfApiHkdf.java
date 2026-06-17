// HKDF through the KDF API (javax.crypto.KDF, JEP 510, Java 25)
//
// Hkdf.java in this directory builds HKDF (RFC 5869) by hand over
// javax.crypto.Mac, because until Java 24 the JDK had no public KDF class.
// Java 25 adds javax.crypto.KDF, an engine class like Cipher or Mac:
// KDF.getInstance("HKDF-SHA256") plus an HKDFParameterSpec that says
// extract-then-expand, extract only, or expand only. Compared with the
// hand-rolled version it buys three things: a provider (an HSM, a FIPS
// module) can do the derivation, deriveKey("AES", ...) returns a SecretKey
// that such a provider can keep non-extractable, and the RFC limits are
// enforced for you. What it does not change: HKDF is for secrets that are
// already high-entropy (DH outputs, master keys), never for passwords, and
// the `info` label is what keeps derived keys independent. Below, the RFC
// vectors are run through both implementations, then the API's edges.
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.TreeSet;
import javax.crypto.KDF;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class KdfApiHkdf {
    static final HexFormat HEX = HexFormat.of();

    // The new way: one call, extract then expand
    static byte[] apiHkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws Exception {
        KDF hkdf = KDF.getInstance("HKDF-SHA256");
        HKDFParameterSpec.Builder b = HKDFParameterSpec.ofExtract().addIKM(ikm);
        if (salt.length > 0) b.addSalt(salt); // no salt = HashLen zero bytes, as in RFC 5869
        return hkdf.deriveData(b.thenExpand(info, length));
    }

    // The old way, condensed from Hkdf.java
    static byte[] manualHkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt.length == 0 ? new byte[32] : salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        ByteArrayOutputStream okm = new ByteArrayOutputStream();
        byte[] t = new byte[0];
        for (int i = 1; okm.size() < length; i++) {
            mac.update(t);
            mac.update(info);
            mac.update((byte) i);
            t = mac.doFinal();
            okm.writeBytes(t);
        }
        return Arrays.copyOf(okm.toByteArray(), length);
    }

    public static void main(String[] args) throws Exception {
        TreeSet<String> hkdfs = new TreeSet<>();
        for (String alg : Security.getAlgorithms("KDF")) if (alg.startsWith("HKDF")) hkdfs.add(alg);
        System.out.println("KDF algorithms: " + hkdfs);

        // RFC 5869 A.1 (basic), A.2 (80-byte inputs), A.3 (no salt, no info)
        byte[] ikm1 = new byte[22];
        Arrays.fill(ikm1, (byte) 0x0b);
        vector("A.1", new byte[0x0d], 0x00, ikm1, HEX.parseHex("f0f1f2f3f4f5f6f7f8f9"), 42,
                "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865");
        vector("A.2", new byte[80], 0x60, range(0x00, 80), range(0xb0, 80), 82,
                "b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c"
                        + "59045a99cac7827271cb41c65e590e09da3275600c2f09b8367793a9aca3db71"
                        + "cc30c58179ec3e87c14c01d5c1f3434f1d87");
        vector("A.3", new byte[0], 0x00, ikm1, new byte[0], 42,
                "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8");

        // Random inputs of many lengths: both implementations agree
        SecureRandom rnd = new SecureRandom();
        int agree = 0;
        for (int len = 1; len <= 200; len++) {
            byte[] salt = new byte[len % 50], ikm = new byte[32 + len % 7], info = new byte[len % 20];
            rnd.nextBytes(salt);
            rnd.nextBytes(ikm);
            rnd.nextBytes(info);
            if (Arrays.equals(apiHkdf(salt, ikm, info, len), manualHkdf(salt, ikm, info, len))) agree++;
        }
        System.out.println("API and hand-rolled agree on " + agree + "/200 random inputs");

        // Extract and expand as separate steps: the PRK stays a SecretKey
        KDF hkdf = KDF.getInstance("HKDF-SHA256");
        byte[] shared = new byte[32]; // stands in for an ECDH or KEM shared secret
        rnd.nextBytes(shared);
        SecretKey prk = hkdf.deriveKey("Generic",
                HKDFParameterSpec.ofExtract().addIKM(shared).addSalt(utf8("handshake v1")).extractOnly());
        SecretKey c2s = hkdf.deriveKey("AES", HKDFParameterSpec.expandOnly(prk, utf8("client->server key"), 32));
        SecretKey s2c = hkdf.deriveKey("AES", HKDFParameterSpec.expandOnly(prk, utf8("server->client key"), 32));
        System.out.println("PRK " + prk.getEncoded().length + " bytes; derived " + c2s.getAlgorithm() + " keys of "
                + c2s.getEncoded().length * 8 + " bits, distinct per label: "
                + !MessageDigest.isEqual(c2s.getEncoded(), s2c.getEncoded()));

        // Several addIKM calls are simply concatenated, with no length framing:
        // the split ("ab", "c") and ("a", "bc") derive the same key. When
        // combining secrets (a hybrid KEM), use fixed-length parts or encode lengths.
        byte[] k1 = hkdf.deriveData(HKDFParameterSpec.ofExtract().addIKM(utf8("ab")).addIKM(utf8("c"))
                .thenExpand(utf8("label"), 16));
        byte[] k2 = hkdf.deriveData(HKDFParameterSpec.ofExtract().addIKM(utf8("a")).addIKM(utf8("bc"))
                .thenExpand(utf8("label"), 16));
        System.out.println("IKM (\"ab\",\"c\") and (\"a\",\"bc\") give the same key: " + Arrays.equals(k1, k2));

        // RFC 5869 caps the output at 255 * HashLen = 8160 bytes for SHA-256;
        // the spec object accepts any length, deriveData enforces the cap
        try {
            hkdf.deriveData(HKDFParameterSpec.expandOnly(prk, new byte[0], 255 * 32 + 1));
            System.out.println("8161-byte request accepted");
        } catch (Exception e) {
            System.out.println("8161-byte request rejected: " + e.getClass().getSimpleName());
        }
        System.out.println("8160-byte request gives " + hkdf.deriveData(HKDFParameterSpec.expandOnly(prk, new byte[0], 8160)).length
                + " bytes");
    }

    static void vector(String name, byte[] salt, int saltStart, byte[] ikm, byte[] info, int length, String okmHex)
            throws Exception {
        for (int i = 0; i < salt.length; i++) salt[i] = (byte) (saltStart + i);
        byte[] api = apiHkdf(salt, ikm, info, length);
        byte[] manual = manualHkdf(salt, ikm, info, length);
        System.out.println("RFC 5869 " + name + ": KDF API " + (HEX.formatHex(api).equals(okmHex) ? "OK" : "MISMATCH")
                + ", hand-rolled " + (HEX.formatHex(manual).equals(okmHex) ? "OK" : "MISMATCH") + " (" + length + " bytes)");
    }

    static byte[] range(int start, int n) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) b[i] = (byte) (start + i);
        return b;
    }

    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}

/* Output:
KDF algorithms: [HKDF-SHA256, HKDF-SHA384, HKDF-SHA512]
RFC 5869 A.1: KDF API OK, hand-rolled OK (42 bytes)
RFC 5869 A.2: KDF API OK, hand-rolled OK (82 bytes)
RFC 5869 A.3: KDF API OK, hand-rolled OK (42 bytes)
API and hand-rolled agree on 200/200 random inputs
PRK 32 bytes; derived AES keys of 256 bits, distinct per label: true
IKM ("ab","c") and ("a","bc") give the same key: true
8161-byte request rejected: InvalidAlgorithmParameterException
8160-byte request gives 8160 bytes
*/
