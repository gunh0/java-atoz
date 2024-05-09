// Unicode often has several encodings for the same visible text: "\u00E9" is
// one precomposed code point, "e\u0301" is 'e' plus a combining accent.
// String.equals sees two different strings. java.text.Normalizer converts
// text into one of four normal forms:
//   NFC   canonical composition    (precomposed where possible)
//   NFD   canonical decomposition  (base letters + combining marks)
//   NFKC  compatibility composition: also folds look-alikes such as the
//         "fi" ligature, full-width letters, circled and superscript digits
//   NFKD  compatibility decomposition
// Normalize before comparing, indexing or validating user input (see
// secure-coding IDS01-J for why validation must come after normalization).
import java.text.Normalizer;
import java.text.Normalizer.Form;

public class NormalizerExample {
    static String codePoints(String s) {
        StringBuilder sb = new StringBuilder();
        s.codePoints().forEach(cp -> sb.append(cp < 128 ? String.valueOf((char) cp) : String.format("<U+%04X>", cp)));
        return sb.toString();
    }

    public static void main(String[] args) {
        String composed = "caf\u00E9";
        String decomposed = "cafe\u0301";
        System.out.println("composed:   " + codePoints(composed) + " length " + composed.length());
        System.out.println("decomposed: " + codePoints(decomposed) + " length " + decomposed.length());
        System.out.println("equals:            " + composed.equals(decomposed));
        System.out.println("equals after NFC:  "
                + Normalizer.normalize(composed, Form.NFC).equals(Normalizer.normalize(decomposed, Form.NFC)));
        System.out.println("isNormalized(NFC): composed " + Normalizer.isNormalized(composed, Form.NFC)
                + ", decomposed " + Normalizer.isNormalized(decomposed, Form.NFC));

        // The four forms side by side
        String sample = "\uFB01le \u2460 x\u00B2 \uFF21\uFF22\uFF23 \u00C5";
        for (Form form : Form.values()) {
            System.out.printf("%-4s %s%n", form, codePoints(Normalizer.normalize(sample, form)));
        }

        // Accent folding for search: decompose, then drop the combining marks
        String query = "Cr\u00E8me Br\u00FBl\u00E9e";
        String folded = Normalizer.normalize(query, Form.NFD).replaceAll("\\p{M}", "");
        System.out.println("folded: " + folded);

        // Look-alike input: a full-width letter passes a naive
        // blacklist but becomes the plain word after NFKC
        String input = "\uFF41dmin";
        System.out.println("naive check \"admin\":       " + input.equals("admin"));
        System.out.println("after NFKC equals \"admin\": " + Normalizer.normalize(input, Form.NFKC).equals("admin"));
    }
}

/* Output:
composed:   caf<U+00E9> length 4
decomposed: cafe<U+0301> length 5
equals:            false
equals after NFC:  true
isNormalized(NFC): composed true, decomposed false
NFD  <U+FB01>le <U+2460> x<U+00B2> <U+FF21><U+FF22><U+FF23> A<U+030A>
NFC  <U+FB01>le <U+2460> x<U+00B2> <U+FF21><U+FF22><U+FF23> <U+00C5>
NFKD file 1 x2 ABC A<U+030A>
NFKC file 1 x2 ABC <U+00C5>
folded: Creme Brulee
naive check "admin":       false
after NFKC equals "admin": true
*/
