// A minimal JWE-style envelope: AES-256-GCM with the header as AAD
//
// JWS (Tokens/JwsCompact.java) signs but does not hide; JWE (RFC 7516)
// encrypts. Its compact form has five parts: protected header, encrypted
// key, IV, ciphertext and authentication tag, all base64url. With "alg":"dir"
// the content key is a pre-shared key and the encrypted-key part is empty;
// with "enc":"A256GCM" the content is AES-256-GCM with a 96-bit IV and a
// 128-bit tag. The detail that makes it sound: the Additional Authenticated
// Data is the ASCII of the encoded protected header, so nobody can change the
// header (kid, alg, enc, an expiry hint) without breaking the tag. As with JWS,
// the receiver checks alg/enc against its own allowlist before decrypting,
// and checks IV and tag lengths. First, the content-encryption step of
// RFC 7516 Appendix A.1 is reproduced with its published key and IV (the
// RSA-OAEP key wrapping there is randomized, so only this step is
// deterministic); then a "dir" envelope is built and attacked. Never reuse an
// IV with the same key: a random 96-bit IV per message is the rule here.
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class JweLikeEnvelope {
    static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    static final Base64.Decoder B64D = Base64.getUrlDecoder();
    static final SecureRandom RNG = new SecureRandom();

    // Returns {ciphertext, tag}
    static byte[][] gcmEncrypt(SecretKey key, byte[] iv, byte[] aad, byte[] plaintext) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        c.updateAAD(aad);
        byte[] out = c.doFinal(plaintext); // ciphertext || tag
        return new byte[][] {Arrays.copyOf(out, out.length - 16), Arrays.copyOfRange(out, out.length - 16, out.length)};
    }

    static String seal(String headerJson, SecretKey key, String plaintext) throws Exception {
        String h = B64.encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        byte[] iv = new byte[12];
        RNG.nextBytes(iv);
        byte[][] ct = gcmEncrypt(key, iv, h.getBytes(StandardCharsets.US_ASCII), plaintext.getBytes(StandardCharsets.UTF_8));
        return h + ".." + B64.encodeToString(iv) + "." + B64.encodeToString(ct[0]) + "." + B64.encodeToString(ct[1]);
    }

    static String open(String token, SecretKey key) throws Exception {
        String[] p = token.split("\\.", -1);
        if (p.length != 5) return "rejected: not five parts";
        String header = new String(B64D.decode(p[0]), StandardCharsets.UTF_8);
        // Allowlist: exactly the algorithms this receiver is configured for
        if (!header.contains("\"alg\":\"dir\"") || !header.contains("\"enc\":\"A256GCM\"")) return "rejected: header not allowed";
        if (!p[1].isEmpty()) return "rejected: dir must have an empty encrypted key";
        byte[] iv = B64D.decode(p[2]);
        byte[] tag = B64D.decode(p[4]);
        if (iv.length != 12 || tag.length != 16) return "rejected: bad IV or tag length";
        byte[] ct = B64D.decode(p[3]);
        byte[] ctAndTag = Arrays.copyOf(ct, ct.length + 16);
        System.arraycopy(tag, 0, ctAndTag, ct.length, 16);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        c.updateAAD(p[0].getBytes(StandardCharsets.US_ASCII));
        try {
            return "ok: " + new String(c.doFinal(ctAndTag), StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            return "rejected: authentication failed";
        }
    }

    static byte[] bytes(int... v) {
        byte[] b = new byte[v.length];
        for (int i = 0; i < v.length; i++) b[i] = (byte) v[i];
        return b;
    }

    public static void main(String[] args) throws Exception {
        // RFC 7516 A.1, content encryption only
        String a1Header = B64.encodeToString("{\"alg\":\"RSA-OAEP\",\"enc\":\"A256GCM\"}".getBytes(StandardCharsets.UTF_8));
        SecretKey cek = new SecretKeySpec(bytes(177, 161, 244, 128, 84, 143, 225, 115, 63, 180, 3, 255, 107, 154,
                212, 246, 138, 7, 110, 91, 112, 46, 34, 105, 47, 130, 203, 46, 122, 234, 64, 252), "AES");
        byte[] a1Iv = bytes(227, 197, 117, 252, 2, 219, 233, 68, 180, 225, 77, 219);
        byte[][] a1 = gcmEncrypt(cek, a1Iv, a1Header.getBytes(StandardCharsets.US_ASCII),
                "The true sign of intelligence is not knowledge but imagination.".getBytes(StandardCharsets.UTF_8));
        System.out.println("A.1 header     " + a1Header);
        System.out.println("A.1 iv         " + B64.encodeToString(a1Iv));
        System.out.println("A.1 ciphertext " + B64.encodeToString(a1[0]));
        System.out.println("A.1 tag        " + B64.encodeToString(a1[1]));
        System.out.println("matches RFC 7516 A.1: " + B64.encodeToString(a1[1]).equals("XFBoMYUZodetZdvTiFvSkQ"));

        // A "dir" envelope with a fresh 256-bit key
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256);
        SecretKey key = kg.generateKey();
        String header = "{\"alg\":\"dir\",\"enc\":\"A256GCM\",\"kid\":\"2025-01\"}";
        String token = seal(header, key, "{\"card\":\"4111 1111 1111 1111\"}");
        String[] p = token.split("\\.", -1);
        System.out.println("parts: header, encrypted key (" + p[1].length() + " chars), iv ("
                + B64D.decode(p[2]).length + " bytes), ciphertext, tag (" + B64D.decode(p[4]).length + " bytes)");
        System.out.println("open:                 " + open(token, key));
        System.out.println("same plaintext twice: " + (seal(header, key, "x").equals(seal(header, key, "x")) ? "same token" : "different tokens"));

        // Attacks
        String otherKid = B64.encodeToString(header.replace("2025-01", "2024-12").getBytes(StandardCharsets.UTF_8));
        System.out.println("kid changed in header: " + open(otherKid + token.substring(p[0].length()), key));
        byte[] ct = B64D.decode(p[3]);
        ct[0] ^= 1;
        System.out.println("ciphertext bit flip:   " + open(String.join(".", p[0], p[1], p[2], B64.encodeToString(ct), p[4]), key));
        System.out.println("tag truncated to 8:    " + open(String.join(".", p[0], p[1], p[2], p[3],
                B64.encodeToString(Arrays.copyOf(B64D.decode(p[4]), 8))), key));
        String otherEnc = B64.encodeToString("{\"alg\":\"dir\",\"enc\":\"A128CBC-HS256\"}".getBytes(StandardCharsets.UTF_8));
        System.out.println("other enc in header:   " + open(otherEnc + token.substring(p[0].length()), key));
        System.out.println("wrong key:             " + open(token, kg.generateKey()));
    }
}

/* Output:
A.1 header     eyJhbGciOiJSU0EtT0FFUCIsImVuYyI6IkEyNTZHQ00ifQ
A.1 iv         48V1_ALb6US04U3b
A.1 ciphertext 5eym8TW_c8SuK0ltJ3rpYIzOeDQz7TALvtu6UG9oMo4vpzs9tX_EFShS8iB7j6jiSdiwkIr3ajwQzaBtQD_A
A.1 tag        XFBoMYUZodetZdvTiFvSkQ
matches RFC 7516 A.1: true
parts: header, encrypted key (0 chars), iv (12 bytes), ciphertext, tag (16 bytes)
open:                 ok: {"card":"4111 1111 1111 1111"}
same plaintext twice: different tokens
kid changed in header: rejected: authentication failed
ciphertext bit flip:   rejected: authentication failed
tag truncated to 8:    rejected: bad IV or tag length
other enc in header:   rejected: header not allowed
wrong key:             rejected: authentication failed
*/
