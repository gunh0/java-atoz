// Length extension: why SHA-256(secret || message) is not a MAC
//
// SHA-256 processes the padded input in 64-byte blocks and its output is
// simply the internal state after the last block. Anyone who knows
// H(secret || m) and the LENGTH of secret || m can load that digest as the
// state, keep hashing, and obtain H(secret || m || padding || extra) without
// knowing the secret. So a "keyed hash" built by prepending a secret lets an
// attacker append data to an authenticated message (the 2009 Flickr API
// signature forgery worked this way). HMAC (Mac.getInstance("HmacSHA256"))
// hashes twice with derived inner/outer keys and is immune; so are SHA-3 and
// SHA-512/256. This program implements the SHA-256 compression function (to
// resume from a digest, which MessageDigest cannot do) and checks it first.
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class LengthExtension {
    public static void main(String[] args) throws Exception {
        byte[] secret = new byte[16]; // known to the server only
        new SecureRandom().nextBytes(secret);
        Server server = new Server(secret);

        byte[] msg = "user=alice&role=user".getBytes(StandardCharsets.US_ASCII);
        byte[] tag = server.naiveTag(msg); // what alice legitimately received
        System.out.println("own SHA-256 matches MessageDigest: "
                + Arrays.equals(Sha256.hash(msg), MessageDigest.getInstance("SHA-256").digest(msg)));

        // Attacker: knows msg, tag and the secret's LENGTH (or tries 1..64)
        byte[] extra = "&role=admin".getBytes(StandardCharsets.US_ASCII);
        int secretLen = 16;
        byte[] gluePadding = Sha256.padding(secretLen + msg.length);
        byte[] forgedMsg = concat(msg, gluePadding, extra);
        byte[] forgedTag = Sha256.extend(tag, secretLen + msg.length + gluePadding.length, extra);

        System.out.println("forged message: \"user=alice&role=user\" + " + gluePadding.length
                + " padding bytes + \"&role=admin\"");
        System.out.println("naive SHA-256(secret||m) accepts forgery: " + server.naiveVerify(forgedMsg, forgedTag));

        // Same attack against HMAC: the attacker can compute nothing useful.
        byte[] hmacTag = server.hmacTag(msg);
        byte[] forgedHmac = Sha256.extend(hmacTag, secretLen + msg.length + gluePadding.length, extra);
        System.out.println("HMAC-SHA256 accepts forgery: " + server.hmacVerify(forgedMsg, forgedHmac));
        System.out.println("HMAC-SHA256 accepts original: " + server.hmacVerify(msg, hmacTag));
    }

    static class Server {
        private final byte[] secret;

        Server(byte[] secret) { this.secret = secret.clone(); }

        byte[] naiveTag(byte[] m) throws Exception {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(secret);
            return md.digest(m);
        }

        boolean naiveVerify(byte[] m, byte[] t) throws Exception {
            return MessageDigest.isEqual(naiveTag(m), t);
        }

        byte[] hmacTag(byte[] m) throws Exception {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(m);
        }

        boolean hmacVerify(byte[] m, byte[] t) throws Exception {
            return MessageDigest.isEqual(hmacTag(m), t);
        }
    }

    // Minimal SHA-256 (FIPS 180-4) whose state can be set from a digest.
    static final class Sha256 {
        static final int[] K = {
            0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
            0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
            0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
            0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2};
        static final int[] IV = {
            0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19};

        static byte[] hash(byte[] m) {
            return run(IV.clone(), concat(m, padding(m.length)));
        }

        // Continue hashing from a published digest; processedLen is the length
        // of everything already absorbed (message + its padding).
        static byte[] extend(byte[] digest, long processedLen, byte[] extra) {
            int[] h = new int[8];
            for (int i = 0; i < 8; i++) {
                h[i] = ((digest[4 * i] & 0xff) << 24) | ((digest[4 * i + 1] & 0xff) << 16)
                        | ((digest[4 * i + 2] & 0xff) << 8) | (digest[4 * i + 3] & 0xff);
            }
            return run(h, concat(extra, padding(processedLen + extra.length)));
        }

        // 0x80, zeros up to 56 mod 64, then the bit length as a 64-bit big-endian number
        static byte[] padding(long len) {
            int zeros = (int) ((55 - len) % 64 + 64) % 64;
            byte[] p = new byte[1 + zeros + 8];
            p[0] = (byte) 0x80;
            long bits = len * 8;
            for (int i = 0; i < 8; i++) p[p.length - 1 - i] = (byte) (bits >>> (8 * i));
            return p;
        }

        static byte[] run(int[] h, byte[] blocks) {
            int[] w = new int[64];
            for (int off = 0; off < blocks.length; off += 64) {
                for (int t = 0; t < 16; t++) {
                    int j = off + 4 * t;
                    w[t] = ((blocks[j] & 0xff) << 24) | ((blocks[j + 1] & 0xff) << 16)
                            | ((blocks[j + 2] & 0xff) << 8) | (blocks[j + 3] & 0xff);
                }
                for (int t = 16; t < 64; t++) {
                    int s0 = Integer.rotateRight(w[t - 15], 7) ^ Integer.rotateRight(w[t - 15], 18) ^ (w[t - 15] >>> 3);
                    int s1 = Integer.rotateRight(w[t - 2], 17) ^ Integer.rotateRight(w[t - 2], 19) ^ (w[t - 2] >>> 10);
                    w[t] = w[t - 16] + s0 + w[t - 7] + s1;
                }
                int a = h[0], b = h[1], c = h[2], d = h[3], e = h[4], f = h[5], g = h[6], hh = h[7];
                for (int t = 0; t < 64; t++) {
                    int t1 = hh + (Integer.rotateRight(e, 6) ^ Integer.rotateRight(e, 11) ^ Integer.rotateRight(e, 25))
                            + ((e & f) ^ (~e & g)) + K[t] + w[t];
                    int t2 = (Integer.rotateRight(a, 2) ^ Integer.rotateRight(a, 13) ^ Integer.rotateRight(a, 22))
                            + ((a & b) ^ (a & c) ^ (b & c));
                    hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2;
                }
                h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh;
            }
            byte[] out = new byte[32];
            for (int i = 0; i < 8; i++) {
                out[4 * i] = (byte) (h[i] >>> 24); out[4 * i + 1] = (byte) (h[i] >>> 16);
                out[4 * i + 2] = (byte) (h[i] >>> 8); out[4 * i + 3] = (byte) h[i];
            }
            return out;
        }
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.writeBytes(p);
        return out.toByteArray();
    }
}

/* Output:
own SHA-256 matches MessageDigest: true
forged message: "user=alice&role=user" + 28 padding bytes + "&role=admin"
naive SHA-256(secret||m) accepts forgery: true
HMAC-SHA256 accepts forgery: false
HMAC-SHA256 accepts original: true
*/
