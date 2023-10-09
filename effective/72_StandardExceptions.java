// Effective Java, Item 72: Favor the use of standard exceptions.
//
// The platform's exceptions cover most needs, and reusing them makes an API
// easier to learn: every Java programmer knows what they mean.
//   IllegalArgumentException      - an inappropriate non-null argument value
//   IllegalStateException         - object not in the right state for the call
//   NullPointerException          - null where it is prohibited
//   IndexOutOfBoundsException     - index parameter out of range
//   ConcurrentModificationException - forbidden concurrent modification
//   UnsupportedOperationException - object does not support the method
// Don't reuse Exception, RuntimeException, Throwable or Error directly.
// java.util.Objects provides the standard checks in one line.
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Objects;

public class StandardExceptions {
    // Anti-pattern: a home-grown exception for a standard situation
    static class BadIndexError extends RuntimeException {
        BadIndexError() {
            super("bad!");
        }
    }

    static final class Deck {
        private final List<String> cards = new ArrayList<>();
        private boolean shuffled;

        Deck add(String card) {
            cards.add(Objects.requireNonNull(card, "card"));
            return this;
        }

        String cardAtHomeGrown(int i) {
            if (i < 0 || i >= cards.size()) {
                throw new BadIndexError();
            }
            return cards.get(i);
        }

        String cardAt(int i) {
            return cards.get(Objects.checkIndex(i, cards.size()));
        }

        Deck deal(int n) {
            if (n <= 0) {
                throw new IllegalArgumentException("n must be positive: " + n);
            }
            if (!shuffled) {
                throw new IllegalStateException("deck must be shuffled before dealing");
            }
            return this;
        }

        List<String> view() {
            return List.copyOf(cards);
        }
    }

    public static void main(String[] args) {
        Deck deck = new Deck().add("A").add("K").add("Q");
        attempt("home-grown index check", () -> deck.cardAtHomeGrown(7));
        attempt("Objects.checkIndex", () -> deck.cardAt(7));
        attempt("requireNonNull", () -> deck.add(null));
        attempt("bad argument", () -> deck.deal(0));
        attempt("wrong state", () -> deck.deal(2));
        attempt("unmodifiable view", () -> deck.view().add("J"));
        attempt("modify while iterating", () -> {
            List<String> l = new ArrayList<>(List.of("x", "y", "z"));
            for (String s : l) {
                if (s.equals("x")) {
                    l.remove(s);
                }
            }
        });
    }

    static void attempt(String label, Runnable r) {
        try {
            r.run();
        } catch (ConcurrentModificationException | UnsupportedOperationException e) {
            System.out.printf("%-23s -> %s%n", label, e.getClass().getSimpleName());
        } catch (RuntimeException e) {
            System.out.printf("%-23s -> %s: %s%n", label, e.getClass().getSimpleName(), e.getMessage());
        }
    }
}

/* Output:
home-grown index check  -> BadIndexError: bad!
Objects.checkIndex      -> IndexOutOfBoundsException: Index 7 out of bounds for length 3
requireNonNull          -> NullPointerException: card
bad argument            -> IllegalArgumentException: n must be positive: 0
wrong state             -> IllegalStateException: deck must be shuffled before dealing
unmodifiable view       -> UnsupportedOperationException
modify while iterating  -> ConcurrentModificationException
*/
