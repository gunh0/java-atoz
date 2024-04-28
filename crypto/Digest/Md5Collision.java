// Why MD5 and SHA-1 are broken: a real MD5 collision you can run
//
// Collision resistance is the property signatures and content addressing
// rely on: if two different inputs share a digest, a signature or allow-list
// entry for one is valid for the other. MD5 collisions were published by
// Wang et al. in 2004 and are now computed in seconds; chosen-prefix
// collisions (two meaningful documents with chosen beginnings) forged a rogue
// CA certificate in 2008 and were used by the Flame malware. SHA-1 fell the
// same way: the SHAttered identical-prefix collision (2017) and a practical
// chosen-prefix collision (2020). Both remain available in the JDK for legacy
// formats; do not use them where an attacker chooses any of the input.
// Below: the two 128-byte messages from Wang's paper, differing in 6 bytes.
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public class Md5Collision {
    static final HexFormat HEX = HexFormat.of();

    static final byte[] M1 = HEX.parseHex(
            "d131dd02c5e6eec4693d9a0698aff95c2fcab58712467eab4004583eb8fb7f89"
            + "55ad340609f4b30283e488832571415a085125e8f7cdc99fd91dbdf280373c5b"
            + "d8823e3156348f5bae6dacd436c919c6dd53e2b487da03fd02396306d248cda0"
            + "e99f33420f577ee8ce54b67080a80d1ec69821bcb6a8839396f9652b6ff72a70");
    static final byte[] M2 = HEX.parseHex(
            "d131dd02c5e6eec4693d9a0698aff95c2fcab50712467eab4004583eb8fb7f89"
            + "55ad340609f4b30283e4888325f1415a085125e8f7cdc99fd91dbd7280373c5b"
            + "d8823e3156348f5bae6dacd436c919c6dd53e23487da03fd02396306d248cda0"
            + "e99f33420f577ee8ce54b67080280d1ec69821bcb6a8839396f965ab6ff72a70");

    public static void main(String[] args) throws Exception {
        int differing = 0;
        for (int i = 0; i < M1.length; i++) if (M1[i] != M2[i]) differing++;
        System.out.println("messages: " + M1.length + " bytes each, " + differing + " bytes differ");

        for (String alg : new String[] {"MD5", "SHA-1", "SHA-256"}) {
            byte[] d1 = MessageDigest.getInstance(alg).digest(M1);
            byte[] d2 = MessageDigest.getInstance(alg).digest(M2);
            System.out.printf("%-7s equal: %-5b  M1 -> %s%n", alg, MessageDigest.isEqual(d1, d2), HEX.formatHex(d1).substring(0, 16) + "...");
        }

        // MD5 is iterative (Merkle-Damgard): once two inputs collide on a block
        // boundary, ANY common suffix keeps them colliding. That is what turns
        // one random-looking collision into two different "documents".
        byte[] suffix = "\nPay 100 EUR to Alice.\n".getBytes(StandardCharsets.US_ASCII);
        byte[] s1 = MessageDigest.getInstance("MD5").digest(concat(M1, suffix));
        byte[] s2 = MessageDigest.getInstance("MD5").digest(concat(M2, suffix));
        System.out.println("MD5 still equal with a common suffix: " + MessageDigest.isEqual(s1, s2));
    }

    static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}

/* Output:
messages: 128 bytes each, 6 bytes differ
MD5     equal: true   M1 -> 79054025255fb1a2...
SHA-1   equal: false  M1 -> a34473cf767c6108...
SHA-256 equal: false  M1 -> 8d12236e5c4ed9f4...
MD5 still equal with a common suffix: true
*/
