// Effective Java, Item 79: Avoid excessive synchronization.
//
// Never cede control to the client inside a synchronized region: calling an
// overridable method or a client-supplied callback ("alien" method) while
// holding a lock invites exceptions, deadlocks and data corruption, because
// you don't know what the alien code does. Here an observable set notifies
// observers while holding its lock. An observer that unsubscribes itself
// triggers ConcurrentModificationException; one that unsubscribes from a
// background thread deadlocks (the background thread waits for the lock the
// notifying thread holds, which waits for the background thread). Fix: call
// alien methods outside the lock - snapshot the list or use
// CopyOnWriteArrayList - and keep synchronized regions short.
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

public class AvoidExcessiveSynchronization {
    interface SetObserver extends BiConsumer<ObservableSet, Integer> {}

    // Broken: observers are called while holding the lock on `observers`
    static class ObservableSet {
        final List<SetObserver> observers;
        private final List<Integer> elements = new ArrayList<>();
        private final boolean alienCallsOutsideLock;

        ObservableSet(boolean fixed) {
            this.alienCallsOutsideLock = fixed;
            this.observers = fixed ? new CopyOnWriteArrayList<>() : new ArrayList<>();
        }

        void addObserver(SetObserver o) {
            synchronized (observers) {
                observers.add(o);
            }
        }

        boolean removeObserver(SetObserver o) {
            synchronized (observers) {
                return observers.remove(o);
            }
        }

        void add(int e) {
            elements.add(e);
            if (alienCallsOutsideLock) {
                for (SetObserver o : observers) { // CopyOnWriteArrayList: no lock, snapshot iteration
                    o.accept(this, e);
                }
            } else {
                synchronized (observers) {
                    for (SetObserver o : observers) {
                        o.accept(this, e); // alien call while holding the lock
                    }
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        for (boolean fixed : new boolean[] {false, true}) {
            String label = fixed ? "fixed " : "broken";

            // Case 1: the observer removes itself during notification
            ObservableSet set = new ObservableSet(fixed);
            List<Integer> seen = new ArrayList<>();
            set.addObserver(new SetObserver() {
                public void accept(ObservableSet s, Integer e) {
                    seen.add(e);
                    if (e == 3) {
                        s.removeObserver(this);
                    }
                }
            });
            try {
                for (int i = 1; i <= 5; i++) {
                    set.add(i);
                }
                System.out.println(label + " self-removal: saw " + seen);
            } catch (java.util.ConcurrentModificationException e) {
                System.out.println(label + " self-removal: ConcurrentModificationException after " + seen);
            }

            // Case 2: the observer removes itself via another thread
            ObservableSet set2 = new ObservableSet(fixed);
            ExecutorService exec = Executors.newSingleThreadExecutor();
            set2.addObserver(new SetObserver() {
                public void accept(ObservableSet s, Integer e) {
                    if (e == 3) {
                        try {
                            exec.submit(() -> s.removeObserver(this)).get(500, TimeUnit.MILLISECONDS);
                        } catch (TimeoutException ex) {
                            System.out.println(label + " background removal: deadlock (timed out)");
                        } catch (Exception ex) {
                            throw new IllegalStateException(ex);
                        }
                    }
                }
            });
            for (int i = 1; i <= 5; i++) {
                set2.add(i);
            }
            exec.shutdown();
            exec.awaitTermination(5, TimeUnit.SECONDS);
            System.out.println(label + " background removal: observers left = " + set2.observers.size());
        }
    }
}

/* Output:
broken self-removal: ConcurrentModificationException after [1, 2, 3]
broken background removal: deadlock (timed out)
broken background removal: observers left = 0
fixed  self-removal: saw [1, 2, 3]
fixed  background removal: observers left = 0
*/
