// Iterator (behavioral): provide a way to access the elements of an
// aggregate object sequentially without exposing its underlying
// representation.
//
// Java builds the pattern into the language: implement Iterable<T> and the
// for-each loop works. Here a log archive stores entries in fixed-size pages
// (its private representation), and two iterators walk it differently: one
// over every entry, one that lazily skips to entries at or above a severity.
// Callers see neither pages nor indexes, and the iterator fails fast if the
// archive is modified during iteration, like the java.util collections do.
// JDK examples: java.util.Iterator / Iterable, Scanner, and Spliterator,
// the stream-friendly successor that can also split work.
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class IteratorDemo {
    enum Level { DEBUG, INFO, WARN, ERROR }

    record Entry(Level level, String message) {
        @Override
        public String toString() { return level + " " + message; }
    }

    // The aggregate: entries are kept in pages of PAGE_SIZE
    static final class LogArchive implements Iterable<Entry> {
        private static final int PAGE_SIZE = 3;
        private final List<Entry[]> pages = new ArrayList<>();
        private int size;
        private int modCount;

        void add(Level level, String message) {
            if (size % PAGE_SIZE == 0) pages.add(new Entry[PAGE_SIZE]);
            pages.get(size / PAGE_SIZE)[size % PAGE_SIZE] = new Entry(level, message);
            size++;
            modCount++;
        }

        int pageCount() { return pages.size(); }

        @Override
        public Iterator<Entry> iterator() { return new PageIterator(); }

        Iterable<Entry> atLeast(Level min) {
            return () -> new FilteringIterator(iterator(), min);
        }

        // The concrete iterator knows the representation; nobody else does
        private final class PageIterator implements Iterator<Entry> {
            private int cursor;
            private final int expectedModCount = modCount;

            public boolean hasNext() { return cursor < size; }

            public Entry next() {
                if (modCount != expectedModCount) throw new ConcurrentModificationException();
                if (!hasNext()) throw new NoSuchElementException();
                Entry e = pages.get(cursor / PAGE_SIZE)[cursor % PAGE_SIZE];
                cursor++;
                return e;
            }
        }
    }

    // An iterator built on another iterator: looks one element ahead
    static final class FilteringIterator implements Iterator<Entry> {
        private final Iterator<Entry> source;
        private final Level min;
        private Entry lookahead;

        FilteringIterator(Iterator<Entry> source, Level min) {
            this.source = source;
            this.min = min;
        }

        public boolean hasNext() {
            while (lookahead == null && source.hasNext()) {
                Entry e = source.next();
                if (e.level().compareTo(min) >= 0) lookahead = e;
            }
            return lookahead != null;
        }

        public Entry next() {
            if (!hasNext()) throw new NoSuchElementException();
            Entry e = lookahead;
            lookahead = null;
            return e;
        }
    }

    public static void main(String[] args) {
        LogArchive archive = new LogArchive();
        archive.add(Level.INFO, "service started");
        archive.add(Level.DEBUG, "cache warmed");
        archive.add(Level.WARN, "certificate expires in 14 days");
        archive.add(Level.INFO, "user alice logged in");
        archive.add(Level.ERROR, "signature verification failed for update.bin");
        archive.add(Level.DEBUG, "gc pause 3 ms");
        archive.add(Level.WARN, "5 failed logins for root");

        System.out.println("all entries (" + archive.pageCount() + " pages inside):");
        for (Entry e : archive) System.out.println("  " + e);

        System.out.println("WARN and above:");
        for (Entry e : archive.atLeast(Level.WARN)) System.out.println("  " + e);

        // Fail-fast: modifying the aggregate invalidates running iterators
        try {
            for (Entry e : archive) {
                if (e.level() == Level.ERROR) archive.add(Level.INFO, "incident opened");
            }
        } catch (ConcurrentModificationException e) {
            System.out.println("modified during iteration: ConcurrentModificationException");
        }

        // Exhausted iterators say so explicitly
        Iterator<Entry> it = new LogArchive().iterator();
        try {
            it.next();
        } catch (NoSuchElementException e) {
            System.out.println("next() on an empty archive: NoSuchElementException");
        }
    }
}

/* Output:
all entries (3 pages inside):
  INFO service started
  DEBUG cache warmed
  WARN certificate expires in 14 days
  INFO user alice logged in
  ERROR signature verification failed for update.bin
  DEBUG gc pause 3 ms
  WARN 5 failed logins for root
WARN and above:
  WARN certificate expires in 14 days
  ERROR signature verification failed for update.bin
  WARN 5 failed logins for root
modified during iteration: ConcurrentModificationException
next() on an empty archive: NoSuchElementException
*/
