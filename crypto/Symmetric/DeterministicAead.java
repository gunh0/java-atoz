// Deterministic encryption: what it leaks, why AES-SIV exists, and the JDK gap
//
// Sometimes you want equal plaintexts to give equal ciphertexts: to look up
// an encrypted e-mail address, to deduplicate, or to wrap keys without a
// nonce. That is deterministic AEAD (DAEAD), and its standard construction
// is AES-SIV (RFC 5297): a MAC over (associated data, plaintext) becomes the
// "synthetic IV" of AES-CTR, so the IV is a function of the message and the
// tag check doubles as integrity. AES-GCM-SIV (RFC 8452) is a faster cousin
// that stays safe if a nonce repeats. The JDK has neither, nor AES-CMAC which
// SIV needs; the usual answer is a library such as Tink (AES-SIV) or BC.
// The caveat that never goes away: determinism leaks equality, so frequency
// analysis works on low-entropy columns. And the wrong way to get it, a fixed
// AES-GCM nonce, is catastrophic (Symmetric/NonceReuse.java). Below: the gap,
// an educational SIV-shaped scheme built from HMAC-SHA256 and AES-CTR (use a
// vetted AES-SIV in production), and what an observer learns from it.
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class DeterministicAead {
    // SIV shape: iv = PRF(macKey, len(aad) || aad || pt)[0..16], ct = AES-CTR(encKey, iv, pt)
    record SivLike(SecretKey macKey, SecretKey encKey) {
        byte[] syntheticIv(byte[] aad, byte[] pt) throws GeneralSecurityException {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(macKey);
            mac.update(ByteBuffer.allocate(8).putLong(aad.length).array()); // unambiguous split of aad/pt
            mac.update(aad);
            return Arrays.copyOf(mac.doFinal(pt), 16);
        }

        byte[] seal(byte[] aad, byte[] pt) throws GeneralSecurityException {
            byte[] iv = syntheticIv(aad, pt);
            Cipher c = Cipher.getInstance("AES/CTR/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, encKey, new IvParameterSpec(iv));
            return ByteBuffer.allocate(16 + pt.length).put(iv).put(c.doFinal(pt)).array();
        }

        byte[] open(byte[] aad, byte[] sealed) throws GeneralSecurityException {
            byte[] iv = Arrays.copyOf(sealed, 16);
            Cipher c = Cipher.getInstance("AES/CTR/NoPadding");
            c.init(Cipher.DECRYPT_MODE, encKey, new IvParameterSpec(iv));
            byte[] pt = c.doFinal(sealed, 16, sealed.length - 16);
            // Recompute the synthetic IV from the decrypted plaintext: it is the tag
            if (!MessageDigest.isEqual(iv, syntheticIv(aad, pt))) throw new GeneralSecurityException("tag mismatch");
            return pt;
        }
    }

    static String available(String kind, String alg) {
        try {
            if (kind.equals("Cipher")) Cipher.getInstance(alg);
            else Mac.getInstance(alg);
            return "available";
        } catch (GeneralSecurityException e) {
            return e.getClass().getSimpleName();
        }
    }

    public static void main(String[] args) throws Exception {
        for (String alg : List.of("AES/SIV/NoPadding", "AES/GCM-SIV/NoPadding")) {
            System.out.printf("Cipher %-22s %s%n", alg, available("Cipher", alg));
        }
        System.out.printf("Mac    %-22s %s%n", "AESCMAC", available("Mac", "AESCMAC"));

        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256);
        SivLike siv = new SivLike(KeyGenerator.getInstance("HmacSHA256").generateKey(), kg.generateKey());
        byte[] aad = "users.email".getBytes(StandardCharsets.UTF_8);
        byte[] email = "alice@example.com".getBytes(StandardCharsets.UTF_8);

        byte[] c1 = siv.seal(aad, email);
        byte[] c2 = siv.seal(aad, email);
        System.out.println("same plaintext, same AAD -> same ciphertext: " + Arrays.equals(c1, c2));
        System.out.println("same plaintext, other AAD -> same ciphertext: "
                + Arrays.equals(c1, siv.seal("orders.email".getBytes(StandardCharsets.UTF_8), email)));
        System.out.println("round trip: " + new String(siv.open(aad, c1), StandardCharsets.UTF_8));
        byte[] tampered = c1.clone();
        tampered[20] ^= 1;
        try {
            siv.open(aad, tampered);
        } catch (GeneralSecurityException e) {
            System.out.println("tampered ciphertext: " + e.getMessage());
        }
        System.out.println("expansion: " + (c1.length - email.length) + " bytes (the synthetic IV is the tag)");

        // What determinism costs: an observer of a low-entropy column counts ciphertexts
        String[] bloodTypes = {"O+", "A+", "O+", "B+", "O+", "A+", "AB-", "O+", "A+", "O+"};
        byte[] col = "patients.blood_type".getBytes(StandardCharsets.UTF_8);
        Map<String, Integer> histogram = new TreeMap<>();
        Map<String, String> label = new TreeMap<>();
        for (String bt : bloodTypes) {
            String ct = HexFormat.of().formatHex(siv.seal(col, bt.getBytes(StandardCharsets.UTF_8)));
            histogram.merge(ct, 1, Integer::sum);
            label.putIfAbsent(ct, bt); // the attacker does not have this; used to print the result
        }
        System.out.println("distinct ciphertexts in the column: " + histogram.size());
        List<Map.Entry<String, Integer>> ranked = histogram.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(e -> label.get(e.getKey())))
                .toList();
        ranked.forEach(e -> System.out.println("  a ciphertext seen " + e.getValue() + "x  (it is " + label.get(e.getKey()) + ")"));
        // O+ is the most common blood type in most populations: match frequencies, no key needed
        System.out.println("guess \"most frequent = O+\" is right: " + label.get(ranked.get(0).getKey()).equals("O+"));
    }
}

/* Output:
Cipher AES/SIV/NoPadding      NoSuchAlgorithmException
Cipher AES/GCM-SIV/NoPadding  NoSuchAlgorithmException
Mac    AESCMAC                NoSuchAlgorithmException
same plaintext, same AAD -> same ciphertext: true
same plaintext, other AAD -> same ciphertext: false
round trip: alice@example.com
tampered ciphertext: tag mismatch
expansion: 16 bytes (the synthetic IV is the tag)
distinct ciphertexts in the column: 4
  a ciphertext seen 5x  (it is O+)
  a ciphertext seen 3x  (it is A+)
  a ciphertext seen 1x  (it is AB-)
  a ciphertext seen 1x  (it is B+)
guess "most frequent = O+" is right: true
*/
