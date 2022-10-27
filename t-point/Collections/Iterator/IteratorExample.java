// An Iterator walks a collection with hasNext()/next() and is the only safe
// way to remove elements DURING iteration: iterator.remove().
// The for-each loop uses an iterator behind the scenes, so calling
// list.remove(...) inside it changes the list under the iterator's feet. The
// standard collections are "fail-fast": they count structural changes
// (modCount) and the next call to next() throws ConcurrentModificationException.
// Despite the name, no second thread is needed. Since Java 8, removeIf() is the
// shortest correct form; ListIterator adds backwards walking, set() and add().
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class IteratorExample {
    public static void main(String[] args) {
        List<String> sessions = sample();

        // 1. Removing inside for-each: ConcurrentModificationException
        try {
            for (String s : sessions) {
                if (s.startsWith("expired")) {
                    sessions.remove(s);
                }
            }
        } catch (ConcurrentModificationException e) {
            System.out.println("for-each + list.remove(): ConcurrentModificationException");
        }

        // 2. Iterator.remove(): removes the element last returned by next()
        sessions = sample();
        Iterator<String> it = sessions.iterator();
        while (it.hasNext()) {
            if (it.next().startsWith("expired")) {
                it.remove();
            }
        }
        System.out.println("Iterator.remove(): " + sessions);

        // remove() twice, or before next(), is illegal
        try {
            Iterator<String> fresh = sessions.iterator();
            fresh.remove();
        } catch (IllegalStateException e) {
            System.out.println("remove() before next(): IllegalStateException");
        }

        // 3. removeIf(): the same thing in one line
        sessions = sample();
        sessions.removeIf(s -> s.startsWith("expired"));
        System.out.println("removeIf(): " + sessions);

        // 4. ListIterator: modify in place and walk backwards
        List<String> words = new ArrayList<>(List.of("alpha", "beta", "gamma"));
        ListIterator<String> li = words.listIterator();
        while (li.hasNext()) {
            int index = li.nextIndex();
            String w = li.next();
            li.set(w.toUpperCase());       // replace the current element
            if (index == 0) {
                li.add("(inserted)");      // insert after it, not visited again
            }
        }
        System.out.println("ListIterator set/add: " + words);
        StringBuilder backwards = new StringBuilder();
        while (li.hasPrevious()) {
            backwards.append(li.previous()).append(' ');
        }
        System.out.println("walking backwards: " + backwards.toString().trim());
    }

    private static List<String> sample() {
        return new ArrayList<>(List.of("s1", "expired-s2", "s3", "expired-s4", "s5"));
    }
}

/* Output:
for-each + list.remove(): ConcurrentModificationException
Iterator.remove(): [s1, s3, s5]
remove() before next(): IllegalStateException
removeIf(): [s1, s3, s5]
ListIterator set/add: [ALPHA, (inserted), BETA, GAMMA]
walking backwards: GAMMA BETA (inserted) ALPHA
*/
