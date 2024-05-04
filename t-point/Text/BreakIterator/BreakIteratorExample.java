// String.length() counts UTF-16 code units, not what a reader sees as
// characters, words or sentences. java.text.BreakIterator finds those
// boundaries for a locale:
//   getCharacterInstance  user-perceived characters (grapheme clusters: a
//                         letter plus combining marks, emoji sequences)
//   getWordInstance       words, but also the spaces and punctuation between
//                         them, so filter the segments you want
//   getSentenceInstance   sentences
// Usage: setText, then walk first()/next() until BreakIterator.DONE; each
// pair of consecutive boundaries delimits one segment.
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BreakIteratorExample {
    static List<String> segments(BreakIterator it, String text) {
        it.setText(text);
        List<String> out = new ArrayList<>();
        int start = it.first();
        for (int end = it.next(); end != BreakIterator.DONE; start = end, end = it.next()) {
            out.add(text.substring(start, end));
        }
        return out;
    }

    static String ascii(Object o) {
        StringBuilder sb = new StringBuilder();
        o.toString().chars().forEach(c -> sb.append(c < 128 ? String.valueOf((char) c) : String.format("\\u%04x", c)));
        return sb.toString();
    }

    public static void main(String[] args) {
        // "cafe" with a combining acute accent, a flag (two regional indicators)
        // and a thumbs-up with a skin-tone modifier
        String text = "cafe\u0301 \uD83C\uDDF0\uD83C\uDDF7 \uD83D\uDC4D\uD83C\uDFFD";
        List<String> graphemes = segments(BreakIterator.getCharacterInstance(Locale.ROOT), text);
        System.out.println("length():       " + text.length());
        System.out.println("code points:    " + text.codePointCount(0, text.length()));
        System.out.println("graphemes:      " + graphemes.size());
        System.out.println("last of 'cafe': " + ascii(graphemes.get(3)));

        // Truncating by code unit can cut a character in half; by grapheme it cannot
        String label = "re\u0301sume\u0301";
        System.out.println("substring(0,2): " + ascii(label.substring(0, 2)));
        BreakIterator chars = BreakIterator.getCharacterInstance(Locale.ROOT);
        chars.setText(label);
        chars.first();
        int end = chars.next(2); // boundary after two graphemes
        System.out.println("first 2 chars:  " + ascii(label.substring(0, end)));

        // Words: keep only segments that start with a letter or digit
        String sentence = "Don't panic: version 3.14 ships on Friday, isn't it?";
        List<String> words = segments(BreakIterator.getWordInstance(Locale.US), sentence).stream()
                .filter(s -> Character.isLetterOrDigit(s.codePointAt(0)))
                .toList();
        System.out.println("words:          " + words);

        // Sentences
        String paragraph = "The scan finished. It found 3 issues! Fix them before Friday? Yes.";
        List<String> sentences = segments(BreakIterator.getSentenceInstance(Locale.US), paragraph);
        for (int i = 0; i < sentences.size(); i++) {
            System.out.println("sentence " + (i + 1) + ":     [" + sentences.get(i).strip() + "]");
        }
    }
}

/* Output:
length():       15
code points:    11
graphemes:      8
last of 'cafe': e\u0301
substring(0,2): re
first 2 chars:  re\u0301
words:          [Don't, panic, version, 3.14, ships, on, Friday, isn't, it]
sentence 1:     [The scan finished.]
sentence 2:     [It found 3 issues!]
sentence 3:     [Fix them before Friday?]
sentence 4:     [Yes.]
*/
