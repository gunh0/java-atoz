// Encoding ML-KEM and ML-DSA keys: X.509 SubjectPublicKeyInfo, PKCS#8, PEM
//
// Post-quantum keys travel in the same containers as RSA and EC keys
// (Keys/PemEncodings.java): a public key is an X.509 SubjectPublicKeyInfo,
// SEQUENCE { AlgorithmIdentifier, BIT STRING }, a private key a PKCS#8
// PrivateKeyInfo, and PEM wraps either in Base64. What is new is the
// AlgorithmIdentifier: NIST assigned one OID per parameter set (ML-KEM-512/
// 768/1024: 2.16.840.1.101.3.4.4.1-3; ML-DSA-44/65/87: 2.16.840.1.101.3.4.3.17-
// 19) with no parameters field, and the BIT STRING holds the raw FIPS 203/204
// public key. KeyFactory "ML-KEM" / "ML-DSA" reads any parameter set and
// reports which one through getParams(). The private-key side is less
// settled: the IETF drafts for the PKCS#8 contents (keep only the 32/64-byte
// seed, the expanded key, or both) were still moving, so treat PQC PKCS#8
// files as provider-specific for now and prefer keeping the key in a key
// store or HSM; this program therefore round-trips private keys but does not
// print their size. A tiny DER reader shows what is inside each public key.
import java.nio.charset.StandardCharsets;
import java.security.AsymmetricKey;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class PqcKeyEncodings {
    // Minimal DER: read tag + length, return {headerLength, contentLength}
    static int[] tlv(byte[] d, int off) {
        int len = d[off + 1] & 0xff, hdr = 2;
        if (len > 0x7f) {
            int n = len & 0x7f;
            len = 0;
            for (int i = 0; i < n; i++) len = (len << 8) | (d[off + 2 + i] & 0xff);
            hdr += n;
        }
        return new int[] {hdr, len};
    }

    static String oid(byte[] d, int off, int len) {
        StringBuilder sb = new StringBuilder();
        int first = d[off] & 0xff;
        sb.append(first / 40).append('.').append(first % 40);
        long v = 0;
        for (int i = off + 1; i < off + len; i++) {
            v = (v << 7) | (d[i] & 0x7f);
            if ((d[i] & 0x80) == 0) { sb.append('.').append(v); v = 0; }
        }
        return sb.toString();
    }

    // SubjectPublicKeyInfo -> "OID, n-byte raw key, n-byte DER overhead"
    static String describeSpki(byte[] spki) {
        int[] outer = tlv(spki, 0);
        int algSeq = outer[0];
        int[] alg = tlv(spki, algSeq);
        int oidAt = algSeq + alg[0];
        int[] o = tlv(spki, oidAt);
        String id = oid(spki, oidAt + o[0], o[1]);
        boolean hasParams = oidAt + o[0] + o[1] < algSeq + alg[0] + alg[1];
        int bitAt = algSeq + alg[0] + alg[1];
        int[] bits = tlv(spki, bitAt);
        int raw = bits[1] - 1; // first content byte: number of unused bits (0)
        return String.format("OID %-24s params %-6s raw key %4d bytes, DER overhead %d", id, hasParams ? "yes" : "absent",
                raw, spki.length - raw);
    }

    static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
                + "\n-----END " + type + "-----\n";
    }

    static byte[] unpem(String pem) {
        return Base64.getMimeDecoder().decode(pem.replaceAll("-----[A-Z ]+-----", ""));
    }

    public static void main(String[] args) throws Exception {
        for (String ps : List.of("ML-KEM-512", "ML-KEM-768", "ML-KEM-1024", "ML-DSA-44", "ML-DSA-65", "ML-DSA-87")) {
            KeyPair kp = KeyPairGenerator.getInstance(ps).generateKeyPair();
            System.out.printf("%-11s %s%n", ps, describeSpki(kp.getPublic().getEncoded()));
        }

        // PEM round trip of an ML-DSA-65 key pair through the generic KeyFactory
        KeyPair kp = KeyPairGenerator.getInstance("ML-DSA-65").generateKeyPair();
        String pubPem = pem("PUBLIC KEY", kp.getPublic().getEncoded());
        String privPem = pem("PRIVATE KEY", kp.getPrivate().getEncoded());
        System.out.println(pubPem.lines().findFirst().orElseThrow() + " ... " + pubPem.lines().count() + " lines");
        KeyFactory kf = KeyFactory.getInstance("ML-DSA");
        PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(unpem(pubPem)));
        PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(unpem(privPem)));
        System.out.println("public key:  format " + pub.getFormat() + ", algorithm " + pub.getAlgorithm()
                + ", parameter set " + ((NamedParameterSpec) ((AsymmetricKey) pub).getParams()).getName()
                + ", round trip equal: " + pub.equals(kp.getPublic()));
        System.out.println("private key: format " + priv.getFormat() + ", parameter set "
                + ((NamedParameterSpec) ((AsymmetricKey) priv).getParams()).getName()
                + ", round trip equal: " + Arrays.equals(priv.getEncoded(), kp.getPrivate().getEncoded()));

        // The OID decides: an ML-DSA key is not an ML-KEM key, whatever the bytes look like
        try {
            KeyFactory.getInstance("ML-KEM").generatePublic(new X509EncodedKeySpec(kp.getPublic().getEncoded()));
            System.out.println("ML-KEM KeyFactory accepted an ML-DSA key?!");
        } catch (InvalidKeySpecException e) {
            System.out.println("ML-KEM KeyFactory, ML-DSA key: InvalidKeySpecException");
        }
        // A truncated public key is refused rather than silently padded
        try {
            byte[] enc = kp.getPublic().getEncoded();
            kf.generatePublic(new X509EncodedKeySpec(Arrays.copyOf(enc, enc.length - 1)));
            System.out.println("truncated key accepted?!");
        } catch (InvalidKeySpecException e) {
            System.out.println("truncated public key: InvalidKeySpecException");
        }
    }
}

/* Output:
ML-KEM-512  OID 2.16.840.1.101.3.4.4.1   params absent raw key  800 bytes, DER overhead 22
ML-KEM-768  OID 2.16.840.1.101.3.4.4.2   params absent raw key 1184 bytes, DER overhead 22
ML-KEM-1024 OID 2.16.840.1.101.3.4.4.3   params absent raw key 1568 bytes, DER overhead 22
ML-DSA-44   OID 2.16.840.1.101.3.4.3.17  params absent raw key 1312 bytes, DER overhead 22
ML-DSA-65   OID 2.16.840.1.101.3.4.3.18  params absent raw key 1952 bytes, DER overhead 22
ML-DSA-87   OID 2.16.840.1.101.3.4.3.19  params absent raw key 2592 bytes, DER overhead 22
-----BEGIN PUBLIC KEY----- ... 44 lines
public key:  format X.509, algorithm ML-DSA, parameter set ML-DSA-65, round trip equal: true
private key: format PKCS#8, parameter set ML-DSA-65, round trip equal: true
ML-KEM KeyFactory, ML-DSA key: InvalidKeySpecException
truncated public key: InvalidKeySpecException
*/
