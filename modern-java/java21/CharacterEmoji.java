// Java 21 — Character.isEmoji and the other Unicode emoji properties
// (API addition, no JEP)
//
// Validating or cleaning user-visible text (display names, chat messages,
// file names) often needs "is this code point an emoji?". Without JDK
// support, code hard-wired a few Unicode block ranges such as
// U+1F600..U+1F64F, which misses most emoji (hearts, people, flags,
// newer additions) and matches nothing else consistently. Java 21 exposes
// the Unicode emoji properties: isEmoji, isEmojiPresentation,
// isEmojiModifier, isEmojiModifierBase, isEmojiComponent and
// isExtendedPictographic, and regex gains \p{IsEmoji} and friends. Note
// the Unicode definition: digits, '#' and '*' have the Emoji property (they
// start keycap sequences) but not Emoji_Presentation.
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CharacterEmoji {
    public static void main(String[] args) {
        // Code points written as numbers so this source and its output stay ASCII
        List<Integer> samples = List.of(
                0x1F600, // grinning face
                0x2764,  // heavy black heart (text style by default)
                0x1F9D1, // person
                0x1F3FD, // skin tone modifier
                0x1F512, // lock
                0x200D,  // zero width joiner (glues emoji sequences)
                (int) '7', (int) '#', (int) 'A');

        System.out.println("code pt  oldRange isEmoji present modifier modBase component pictographic");
        for (int cp : samples) {
            System.out.printf("U+%-5X  %-8b %-7b %-7b %-8b %-7b %-9b %b%n", cp,
                    oldRangeCheck(cp),
                    Character.isEmoji(cp),
                    Character.isEmojiPresentation(cp),
                    Character.isEmojiModifier(cp),
                    Character.isEmojiModifierBase(cp),
                    Character.isEmojiComponent(cp),
                    Character.isExtendedPictographic(cp));
        }

        // A display name that should not carry emoji (e.g. to avoid look-alike or spoofed names)
        String name = new StringBuilder("admin").appendCodePoint(0x1F512)
                .append(" team").appendCodePoint(0x2764).append(" 7").toString();

        // Before: the block-range check lets most emoji through
        String oldClean = name.codePoints().filter(cp -> !oldRangeCheck(cp))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
        // After: Unicode properties; keep digits, which are "Emoji" only as keycap bases
        String clean = name.codePoints().filter(cp -> !Character.isEmoji(cp) || Character.isDigit(cp))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
        System.out.println("old range filter removes " + (name.length() - oldClean.length()) + " chars -> still has emoji: "
                + oldClean.codePoints().anyMatch(cp -> Character.isEmoji(cp) && !Character.isDigit(cp)));
        System.out.println("isEmoji filter: \"" + clean + "\"");

        // Regex: \p{IsEmoji_Presentation} finds emoji rendered as pictures by default
        Pattern pictures = Pattern.compile("\\p{IsEmoji_Presentation}");
        String found = pictures.matcher(name).results()
                .map(r -> String.format("U+%X", r.group().codePointAt(0)))
                .collect(Collectors.joining(", "));
        System.out.println("\\p{IsEmoji_Presentation} matches: " + found);
        System.out.println("\\p{IsEmoji} matches '7': " + Pattern.matches("\\p{IsEmoji}", "7"));
    }

    // The pre-Java 21 shortcut: only the "Emoticons" block
    static boolean oldRangeCheck(int cp) {
        return cp >= 0x1F600 && cp <= 0x1F64F;
    }
}

/* Output:
code pt  oldRange isEmoji present modifier modBase component pictographic
U+1F600  true     true    true    false    false   false     true
U+2764   false    true    false   false    false   false     true
U+1F9D1  false    true    true    false    true    false     true
U+1F3FD  false    true    true    true     false   true      false
U+1F512  false    true    true    false    false   false     true
U+200D   false    false   false   false    false   true      false
U+37     false    true    false   false    false   true      false
U+23     false    true    false   false    false   true      false
U+41     false    false   false   false    false   false     false
old range filter removes 0 chars -> still has emoji: true
isEmoji filter: "admin team 7"
\p{IsEmoji_Presentation} matches: U+1F512
\p{IsEmoji} matches '7': true
*/
