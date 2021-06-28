// Effective Java, Item 5: Prefer dependency injection to hardwiring resources.
//
// A class whose behavior depends on an underlying resource (a dictionary, a
// clock, a data source) should not create that resource itself, whether as a
// static utility or a singleton. Hardwiring makes the class inflexible (one
// dictionary for every language) and hard to test. Instead, pass the resource
// (or a factory for it) into the constructor. The object stays immutable, can
// be shared, and each client chooses the resource it needs.
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

public class DependencyInjectionDemo {
    public static void main(String[] args) {
        // Hardwired: always English, and a test cannot swap the dictionary
        System.out.println("static checker, 'colour': " + StaticSpellChecker.isValid("colour"));

        // Injected: the same class works with any Lexicon
        Lexicon american = new SetLexicon("en-US", Set.of("color", "center", "java"));
        Lexicon british = new SetLexicon("en-GB", Set.of("colour", "centre", "java"));

        SpellChecker us = new SpellChecker(american);
        SpellChecker uk = new SpellChecker(british);
        System.out.println("en-US checker, 'colour': " + us.isValid("colour"));
        System.out.println("en-GB checker, 'colour': " + uk.isValid("colour"));

        // In a test, inject a tiny fake instead of a real dictionary
        SpellChecker test = new SpellChecker(word -> word.startsWith("ok"));
        System.out.println("fake lexicon, 'okay': " + test.isValid("OKAY"));

        // Variant: inject a factory (Supplier<? extends T>) when the class must
        // create fresh resources on demand
        Supplier<Lexicon> factory = () -> new SetLexicon("en-US", Set.of("java"));
        LexiconPool pool = new LexiconPool(factory);
        System.out.println("pool created distinct lexicons: " + (pool.create() != pool.create()));
    }
}

interface Lexicon {
    boolean contains(String word);
}

record SetLexicon(String tag, Set<String> words) implements Lexicon {
    @Override
    public boolean contains(String word) {
        return words.contains(word);
    }
}

// Anti-pattern: a static utility hardwired to one resource
final class StaticSpellChecker {
    private static final Lexicon DICTIONARY = new SetLexicon("en-US", Set.of("color", "center", "java"));

    private StaticSpellChecker() {
    }

    static boolean isValid(String word) {
        return DICTIONARY.contains(word.toLowerCase(Locale.ROOT));
    }
}

// Recommended: the dependency comes in through the constructor
final class SpellChecker {
    private final Lexicon dictionary;

    SpellChecker(Lexicon dictionary) {
        this.dictionary = java.util.Objects.requireNonNull(dictionary);
    }

    boolean isValid(String word) {
        return dictionary.contains(word.toLowerCase(Locale.ROOT));
    }
}

// Injecting a factory instead of an instance
final class LexiconPool {
    private final Supplier<? extends Lexicon> factory;

    LexiconPool(Supplier<? extends Lexicon> factory) {
        this.factory = factory;
    }

    Lexicon create() {
        return factory.get();
    }
}

/* Output:
static checker, 'colour': false
en-US checker, 'colour': false
en-GB checker, 'colour': true
fake lexicon, 'okay': true
pool created distinct lexicons: true
*/
