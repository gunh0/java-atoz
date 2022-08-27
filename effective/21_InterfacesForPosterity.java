// Effective Java, Item 21: Design interfaces for posterity.
//
// Default methods let you add methods to an existing interface, but they are
// injected into every implementation without its author's knowledge. A
// default can only be written in terms of the other interface methods, so it
// may break the invariants of implementations that were written before it
// existed. Example: Collection.removeIf arrived in Java 8 as a default that
// removes through the iterator. A collection written earlier that audits
// every remove() call is silently bypassed. Design interfaces carefully up
// front, and when adding a default, test it against existing implementations.
import java.util.ArrayList;
import java.util.Collection;
import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

public class InterfacesForPosterity {
    public static void main(String[] args) {
        AuditedCollection<String> legacy = new AuditedCollection<>(List.of("a", "b", "c", "d"));
        legacy.remove("a");
        legacy.removeIf(s -> s.compareTo("c") < 0); // inherited default
        System.out.println("legacy contents: " + legacy + ", audit log: " + legacy.auditLog());

        FixedAuditedCollection<String> fixed = new FixedAuditedCollection<>(List.of("a", "b", "c", "d"));
        fixed.remove("a");
        fixed.removeIf(s -> s.compareTo("c") < 0);
        System.out.println("fixed contents:  " + fixed + ", audit log: " + fixed.auditLog());
    }
}

// Written "before Java 8": every removal must be logged
class AuditedCollection<E> extends AbstractCollection<E> {
    protected final List<E> elements;
    protected final List<String> audit = new ArrayList<>();

    AuditedCollection(Collection<E> initial) {
        elements = new ArrayList<>(initial);
    }

    @Override
    public boolean remove(Object o) {
        boolean removed = elements.remove(o);
        if (removed) {
            audit.add("removed " + o);
        }
        return removed;
    }

    // The iterator comes from the backing list, so iterator().remove()
    // (which the removeIf default uses) skips the audit entirely.
    @Override
    public Iterator<E> iterator() {
        return elements.iterator();
    }

    @Override
    public int size() {
        return elements.size();
    }

    List<String> auditLog() {
        return audit;
    }
}

// Updated after the new default appeared: override it to keep the invariant
class FixedAuditedCollection<E> extends AuditedCollection<E> {
    FixedAuditedCollection(Collection<E> initial) {
        super(initial);
    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        boolean any = false;
        for (Iterator<E> it = elements.iterator(); it.hasNext(); ) {
            E e = it.next();
            if (filter.test(e)) {
                it.remove();
                audit.add("removed " + e);
                any = true;
            }
        }
        return any;
    }
}

/* Output:
legacy contents: [c, d], audit log: [removed a]
fixed contents:  [c, d], audit log: [removed a, removed b]
*/
