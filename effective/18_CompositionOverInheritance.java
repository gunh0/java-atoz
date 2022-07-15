// Effective Java, Item 18: Favor composition over inheritance.
//
// Extending a concrete class from another package couples you to its
// implementation details: whether its methods call each other ("self-use")
// is usually undocumented and may change in the next release. Below,
// HashSet.addAll happens to call add for each element, so a subclass that
// counts in both methods counts every element twice. Composition avoids
// this: keep the existing object in a private field and forward calls to it
// (a "wrapper", or Decorator). The wrapper relies only on the documented
// interface, works with any Set implementation, and survives upgrades.
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class CompositionOverInheritance {
    public static void main(String[] args) {
        List<String> words = List.of("snap", "crackle", "pop");

        InstrumentedHashSet<String> inherited = new InstrumentedHashSet<>();
        inherited.addAll(words);
        System.out.println("inheritance: addCount = " + inherited.getAddCount() + " (expected 3)");

        InstrumentedSet<String> composed = new InstrumentedSet<>(new HashSet<>());
        composed.addAll(words);
        System.out.println("composition: addCount = " + composed.getAddCount());

        // The wrapper instruments any Set, e.g. a sorted one
        InstrumentedSet<String> sorted = new InstrumentedSet<>(new TreeSet<>());
        sorted.add("b");
        sorted.addAll(List.of("c", "a"));
        System.out.println("wrapped TreeSet: " + sorted + ", addCount = " + sorted.getAddCount());
    }
}

// Broken: depends on HashSet's undocumented self-use of add() inside addAll()
class InstrumentedHashSet<E> extends HashSet<E> {
    private int addCount = 0;

    @Override
    public boolean add(E e) {
        addCount++;
        return super.add(e);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        addCount += c.size();
        return super.addAll(c);
    }

    int getAddCount() {
        return addCount;
    }
}

// Reusable forwarding class: implements Set by delegating every call
class ForwardingSet<E> implements Set<E> {
    private final Set<E> s;

    ForwardingSet(Set<E> s) {
        this.s = s;
    }

    public void clear() { s.clear(); }
    public boolean contains(Object o) { return s.contains(o); }
    public boolean isEmpty() { return s.isEmpty(); }
    public int size() { return s.size(); }
    public Iterator<E> iterator() { return s.iterator(); }
    public boolean add(E e) { return s.add(e); }
    public boolean remove(Object o) { return s.remove(o); }
    public boolean containsAll(Collection<?> c) { return s.containsAll(c); }
    public boolean addAll(Collection<? extends E> c) { return s.addAll(c); }
    public boolean removeAll(Collection<?> c) { return s.removeAll(c); }
    public boolean retainAll(Collection<?> c) { return s.retainAll(c); }
    public Object[] toArray() { return s.toArray(); }
    public <T> T[] toArray(T[] a) { return s.toArray(a); }
    @Override public boolean equals(Object o) { return s.equals(o); }
    @Override public int hashCode() { return s.hashCode(); }
    @Override public String toString() { return s.toString(); }
}

// Wrapper class: adds instrumentation on top of any Set
class InstrumentedSet<E> extends ForwardingSet<E> {
    private int addCount = 0;

    InstrumentedSet(Set<E> s) {
        super(s);
    }

    @Override
    public boolean add(E e) {
        addCount++;
        return super.add(e);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        addCount += c.size();
        return super.addAll(c); // forwards to the wrapped set, not to this.add
    }

    int getAddCount() {
        return addCount;
    }
}

/* Output:
inheritance: addCount = 6 (expected 3)
composition: addCount = 3
wrapped TreeSet: [a, b, c], addCount = 3
*/
