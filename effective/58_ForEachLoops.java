// Effective Java, Item 58: Prefer for-each loops to traditional for loops.
//
// Index variables and explicit iterators are clutter, and each mention of
// them is a chance to use the wrong one. The classic bug is calling
// i.next() for the outer collection inside the inner loop: it either throws
// NoSuchElementException or, worse, silently produces too few results. The
// for-each loop hides the iterator or index entirely, works for arrays and
// any Iterable, and costs nothing. Three cases still need the explicit form:
// destructive filtering (use Collection.removeIf), transforming elements in
// place (ListIterator.set or an index), and parallel iteration over several
// collections in lockstep.
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

public class ForEachLoops {
    enum Face { ONE, TWO, THREE, FOUR, FIVE, SIX }

    enum Suit { CLUB, DIAMOND, HEART, SPADE }

    enum Rank { ACE, DEUCE, THREE, FOUR, FIVE }

    public static void main(String[] args) {
        Collection<Face> faces = EnumSet.allOf(Face.class);

        // Bug: i.next() is called once per INNER iteration, so only 6 pairs come out
        List<String> rolls = new ArrayList<>();
        for (Iterator<Face> i = faces.iterator(); i.hasNext();) {
            for (Iterator<Face> j = faces.iterator(); j.hasNext();) {
                rolls.add(i.next() + "+" + j.next());
            }
        }
        System.out.println("buggy dice: " + rolls.size() + " pairs " + rolls);

        // Same bug with collections of different sizes: it throws
        Collection<Suit> suits = EnumSet.allOf(Suit.class);
        Collection<Rank> ranks = EnumSet.allOf(Rank.class);
        try {
            List<String> deck = new ArrayList<>();
            for (Iterator<Suit> i = suits.iterator(); i.hasNext();) {
                for (Iterator<Rank> j = ranks.iterator(); j.hasNext();) {
                    deck.add(i.next() + " " + j.next());
                }
            }
        } catch (NoSuchElementException e) {
            System.out.println("buggy deck: NoSuchElementException");
        }

        // Nested for-each: correct and shorter
        int pairs = 0;
        for (Face f1 : faces) {
            for (Face f2 : faces) {
                pairs++;
            }
        }
        List<String> deck = new ArrayList<>();
        for (Suit suit : suits) {
            for (Rank rank : ranks) {
                deck.add(rank + " of " + suit);
            }
        }
        System.out.println("for-each dice: " + pairs + " pairs");
        System.out.println("for-each deck: " + deck.size() + " cards, first " + deck.get(0)
                + ", last " + deck.get(deck.size() - 1));

        // Where for-each cannot be used
        List<Integer> numbers = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6));
        numbers.removeIf(n -> n % 2 == 0);                          // destructive filtering
        System.out.println("removeIf: " + numbers);
        for (ListIterator<Integer> it = numbers.listIterator(); it.hasNext();) {
            it.set(it.next() * 10);                                  // transforming in place
        }
        System.out.println("ListIterator.set: " + numbers);
        String[] names = { "x", "y", "z" };
        for (int k = 0; k < names.length; k++) {                     // parallel iteration
            System.out.print(names[k] + "=" + numbers.get(k) + (k < names.length - 1 ? ", " : "\n"));
        }
    }
}

/* Output:
buggy dice: 6 pairs [ONE+ONE, TWO+TWO, THREE+THREE, FOUR+FOUR, FIVE+FIVE, SIX+SIX]
buggy deck: NoSuchElementException
for-each dice: 36 pairs
for-each deck: 20 cards, first ACE of CLUB, last FIVE of SPADE
removeIf: [1, 3, 5]
ListIterator.set: [10, 30, 50]
x=10, y=30, z=50
*/
