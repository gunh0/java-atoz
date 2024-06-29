// AES-GCM: authenticated encryption with associated data (AEAD)
//
// GCM = CTR-mode encryption + a GHASH authenticator over the ciphertext and
// over "associated data" (AAD): bytes that are not secret but must not be
// changed, such as a header, a record ID or a protocol version. Decryption
// verifies the 128-bit tag before releasing any plaintext and throws
// AEADBadTagException on any change to ciphertext, AAD, tag or nonce. The
// rules: a 96-bit (12-byte) nonce that is NEVER reused with the same key
// (random nonces are fine for up to about 2^32 messages per key; a repeat
// leaks plaintext XORs and the authentication key), tag length 128 bits, and
// a stored format of nonce || ciphertext || tag. Java's Cipher even refuses to be
// re-initialized for encryption with the key and IV it just used.
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AesGcm {
    public static void main(String[] args) throws Exception {
        // Known answers: McGrew & Viega, GCM spec test cases 1 and 2 (all-zero key and IV)
        HexFormat hex = HexFormat.of();
        Cipher kat = Cipher.getInstance("AES/GCM/NoPadding");
        kat.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(new byte[16], "AES"), new GCMParameterSpec(128, new byte[12]));
        System.out.println("test case 1 tag:     " + (hex.formatHex(kat.doFinal()).equals("58e2fccefa7e3061367f1d57a4e7455a") ? "OK" : "MISMATCH"));
        Cipher kat2 = Cipher.getInstance("AES/GCM/NoPadding");
        kat2.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(new byte[16], "AES"), new GCMParameterSpec(128, new byte[12]));
        System.out.println("test case 2 ct||tag: " + (hex.formatHex(kat2.doFinal(new byte[16])).equals(
                "0388dace60b6a392f328c2b971b2fe78" + "ab6e47d42cec13bdf53a67b21257bddf") ? "OK" : "MISMATCH"));

        SecretKey key = KeyGenerator.getInstance("AES").generateKey();
        byte[] aad = "record=17;v=2".getBytes(StandardCharsets.US_ASCII);
        byte[] plaintext = "salary: 120000".getBytes(StandardCharsets.US_ASCII);

        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        Cipher enc = Cipher.getInstance("AES/GCM/NoPadding");
        enc.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        enc.updateAAD(aad);
        byte[] ct = enc.doFinal(plaintext);
        System.out.println();
        System.out.println("plaintext " + plaintext.length + " bytes -> ciphertext+tag " + ct.length + " bytes");
        System.out.println("genuine:        " + open(key, nonce, aad, ct));

        byte[] badCt = ct.clone();
        badCt[0] ^= 1;
        System.out.println("ciphertext bit: " + open(key, nonce, aad, badCt));
        byte[] badTag = ct.clone();
        badTag[badTag.length - 1] ^= 1;
        System.out.println("tag bit:        " + open(key, nonce, aad, badTag));
        System.out.println("other AAD:      " + open(key, nonce, "record=18;v=2".getBytes(StandardCharsets.US_ASCII), ct));
        byte[] otherNonce = nonce.clone();
        otherNonce[0] ^= 1;
        System.out.println("other nonce:    " + open(key, otherNonce, aad, ct));

        // Built-in guard against the most common GCM mistake
        try {
            enc.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        } catch (InvalidAlgorithmParameterException e) {
            System.out.println("re-init with the same key and nonce: " + e.getClass().getSimpleName());
        }
    }

    static String open(SecretKey key, byte[] nonce, byte[] aad, byte[] ct) throws Exception {
        Cipher dec = Cipher.getInstance("AES/GCM/NoPadding");
        dec.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        dec.updateAAD(aad);
        try {
            return "\"" + new String(dec.doFinal(ct), StandardCharsets.US_ASCII) + "\"";
        } catch (AEADBadTagException e) {
            return "rejected (" + e.getClass().getSimpleName() + ")";
        }
    }
}

/* Output:
test case 1 tag:     OK
test case 2 ct||tag: OK

plaintext 14 bytes -> ciphertext+tag 30 bytes
genuine:        "salary: 120000"
ciphertext bit: rejected (AEADBadTagException)
tag bit:        rejected (AEADBadTagException)
other AAD:      rejected (AEADBadTagException)
other nonce:    rejected (AEADBadTagException)
re-init with the same key and nonce: InvalidAlgorithmParameterException
*/
