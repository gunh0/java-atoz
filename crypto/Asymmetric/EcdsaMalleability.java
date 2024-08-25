// ECDSA malleability: (r, s) and (r, n - s) are both valid signatures
//
// ECDSA verification only uses the x-coordinate of a computed point, and
// negating s negates that point without changing x. So for every valid
// signature (r, s) on a message, (r, n - s) is another valid signature on
// the SAME message, computable by anyone without the private key. That is
// harmless for authenticity, but fatal for systems that treat signature
// bytes as unique: replay caches keyed by signature, "seen" sets of signed
// tokens, transaction IDs that hash the signature (Bitcoin's 2014 Mt. Gox
// dispute). Fixes: identify messages by their content, not their signature,
// or require the canonical "low-s" form (s <= n/2), as Bitcoin and Ethereum
// do. Java's verifier accepts both forms, so the check is your job.
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

public class EcdsaMalleability {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = kpg.generateKeyPair();
        BigInteger n = ((ECPublicKey) kp.getPublic()).getParams().getOrder();
        System.out.println("P-256 group order n = " + n.toString(16).substring(0, 16) + "... (" + n.bitLength() + " bits)");

        byte[] msg = "pay 5 coins to bob, nonce 7".getBytes(StandardCharsets.US_ASCII);
        byte[] sig = sign(kp, msg);

        // Anyone can flip s
        byte[] flipped = flip(sig, n);
        System.out.println("original verifies: " + verify(kp, msg, sig));
        System.out.println("(r, n-s) verifies: " + verify(kp, msg, flipped));
        System.out.println("same bytes: " + Arrays.equals(sig, flipped));

        // Noncompliant: a replay cache keyed by signature lets the payment through twice
        Set<String> seenSignatures = new HashSet<>();
        int paid = 0;
        for (byte[] s : new byte[][] {sig, flipped}) {
            if (verify(kp, msg, s) && seenSignatures.add(HexFormat.of().formatHex(s))) paid++;
        }
        System.out.println("replay cache by signature: payment executed " + paid + " times");

        // Compliant: canonical low-s only (and/or dedupe by message content)
        paid = 0;
        Set<String> seenMessages = new HashSet<>();
        for (byte[] s : new byte[][] {sig, flipped}) {
            if (isLowS(s, n) && verify(kp, msg, s) && seenMessages.add(new String(msg, StandardCharsets.US_ASCII))) paid++;
        }
        System.out.println("low-s rule + cache by message: payment executed " + paid + " time");
        System.out.println("exactly one of the two forms is low-s: " + (isLowS(sig, n) != isLowS(flipped, n)));
    }

    // Signatures are in P1363 form here (r || s, 32 bytes each) to keep the arithmetic simple
    static byte[] flip(byte[] sig, BigInteger n) {
        BigInteger s = new BigInteger(1, Arrays.copyOfRange(sig, 32, 64));
        byte[] out = sig.clone();
        byte[] ns = n.subtract(s).toByteArray();
        Arrays.fill(out, 32, 64, (byte) 0);
        int copy = Math.min(ns.length, 32);
        System.arraycopy(ns, ns.length - copy, out, 64 - copy, copy);
        return out;
    }

    static boolean isLowS(byte[] sig, BigInteger n) {
        return new BigInteger(1, Arrays.copyOfRange(sig, 32, 64)).compareTo(n.shiftRight(1)) <= 0;
    }

    static byte[] sign(KeyPair kp, byte[] msg) throws Exception {
        Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
        s.initSign(kp.getPrivate());
        s.update(msg);
        return s.sign();
    }

    static boolean verify(KeyPair kp, byte[] msg, byte[] sig) throws Exception {
        Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
        s.initVerify(kp.getPublic());
        s.update(msg);
        return s.verify(sig);
    }
}

/* Output:
P-256 group order n = ffffffff00000000... (256 bits)
original verifies: true
(r, n-s) verifies: true
same bytes: false
replay cache by signature: payment executed 2 times
low-s rule + cache by message: payment executed 1 time
exactly one of the two forms is low-s: true
*/
