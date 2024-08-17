// ECDSA on P-256, and the two ways to encode a signature (DER vs P1363)
//
// An ECDSA signature is a pair of integers (r, s), each smaller than the
// curve order. Java's "SHA256withECDSA" encodes it as an ASN.1 DER SEQUENCE
// of two INTEGERs (what X.509 and TLS use): variable length, 70-72 bytes
// for P-256, because DER integers drop leading zeros and add a 0x00 when the
// top bit is set. JOSE/JWT (ES256), WebAuthn's raw format, PKCS#11 and many
// HSMs use IEEE P1363 instead: r || s as fixed 32-byte big-endian values,
// always 64 bytes; Java calls that "SHA256withECDSAinP1363Format". Mixing them
// up is a classic interop bug (and a strict DER parser is a security
// requirement, since lenient parsing allowed malleable signatures). ECDSA
// also needs a fresh random nonce per signature, which Java handles.
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;

public class EcdsaSignatureEncoding {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1")); // NIST P-256
        KeyPair kp = kpg.generateKeyPair();
        byte[] msg = "{\"sub\":\"alice\",\"admin\":false}".getBytes(StandardCharsets.UTF_8);

        // DER: length varies from signature to signature
        int minLen = Integer.MAX_VALUE, maxLen = 0;
        boolean allDer = true;
        for (int i = 0; i < 200; i++) {
            byte[] der = sign("SHA256withECDSA", kp, msg);
            minLen = Math.min(minLen, der.length);
            maxLen = Math.max(maxLen, der.length);
            allDer &= der[0] == 0x30 && der[2] == 0x02; // SEQUENCE { INTEGER r, ... }
        }
        System.out.println("DER signatures: start with SEQUENCE/INTEGER: " + allDer
                + ", lengths within 68..72: " + (minLen >= 68 && maxLen <= 72) + ", length varies: " + (minLen != maxLen));

        // P1363: always 64 bytes
        byte[] p1363 = sign("SHA256withECDSAinP1363Format", kp, msg);
        System.out.println("P1363 signature length: " + p1363.length);
        System.out.println("P1363 verifies as P1363: " + verify("SHA256withECDSAinP1363Format", kp, msg, p1363));
        System.out.println("P1363 verifies as DER:   " + verify("SHA256withECDSA", kp, msg, p1363));

        // Converting is easy once you know the layout
        byte[] der = sign("SHA256withECDSA", kp, msg);
        byte[] converted = derToP1363(der, 32);
        System.out.println("DER converted to P1363 verifies: " + verify("SHA256withECDSAinP1363Format", kp, msg, converted));
        System.out.println("and back to DER verifies: " + verify("SHA256withECDSA", kp, msg, p1363ToDer(converted)));
        System.out.println("round trip preserves bytes: " + Arrays.equals(der, p1363ToDer(converted)));
    }

    // DER: 30 len 02 lenR R 02 lenS S (P-256 lengths fit in one byte)
    static byte[] derToP1363(byte[] der, int size) {
        int rLen = der[3];
        BigInteger r = new BigInteger(1, Arrays.copyOfRange(der, 4, 4 + rLen));
        int sLen = der[5 + rLen];
        BigInteger s = new BigInteger(1, Arrays.copyOfRange(der, 6 + rLen, 6 + rLen + sLen));
        byte[] out = new byte[2 * size];
        put(r, out, 0, size);
        put(s, out, size, size);
        return out;
    }

    static byte[] p1363ToDer(byte[] raw) {
        byte[] r = new BigInteger(1, Arrays.copyOfRange(raw, 0, raw.length / 2)).toByteArray(); // minimal, signed
        byte[] s = new BigInteger(1, Arrays.copyOfRange(raw, raw.length / 2, raw.length)).toByteArray();
        byte[] der = new byte[6 + r.length + s.length];
        der[0] = 0x30; der[1] = (byte) (4 + r.length + s.length);
        der[2] = 0x02; der[3] = (byte) r.length;
        System.arraycopy(r, 0, der, 4, r.length);
        der[4 + r.length] = 0x02; der[5 + r.length] = (byte) s.length;
        System.arraycopy(s, 0, der, 6 + r.length, s.length);
        return der;
    }

    static void put(BigInteger v, byte[] out, int off, int size) {
        byte[] b = v.toByteArray(); // may carry a leading 0x00 sign byte
        int copy = Math.min(b.length, size);
        System.arraycopy(b, b.length - copy, out, off + size - copy, copy);
    }

    static byte[] sign(String alg, KeyPair kp, byte[] msg) throws Exception {
        Signature s = Signature.getInstance(alg);
        s.initSign(kp.getPrivate());
        s.update(msg);
        return s.sign();
    }

    static boolean verify(String alg, KeyPair kp, byte[] msg, byte[] sig) throws Exception {
        Signature s = Signature.getInstance(alg);
        s.initVerify(kp.getPublic());
        s.update(msg);
        try {
            return s.verify(sig);
        } catch (SignatureException e) {
            return false;
        }
    }
}

/* Output:
DER signatures: start with SEQUENCE/INTEGER: true, lengths within 68..72: true, length varies: true
P1363 signature length: 64
P1363 verifies as P1363: true
P1363 verifies as DER:   false
DER converted to P1363 verifies: true
and back to DER verifies: true
round trip preserves bytes: true
*/
