// OWASP Top 10 2021: A04:2021 Insecure Design, business-logic race / double spend (CWE-362, CWE-367)
// https://owasp.org/Top10/A04_2021-Insecure_Design/
//
// "Check the balance, then debit it" is two steps. When two requests for the
// same account arrive together (two browser tabs, a scripted burst, a
// retried mobile call), both can pass the check before either debit lands,
// and a 100.00 balance pays out 80.00 twice. Each request is valid on its
// own; the bug is in the design, so input validation cannot catch it. Make
// the check and the update one atomic step (a lock, a compare-and-set loop,
// or "UPDATE ... SET balance = balance - ? WHERE balance >= ?" in SQL), and
// give each client operation an idempotency key so a resubmitted request is
// answered from the first result instead of being executed again.
// The demo forces the dangerous interleaving with a barrier, so it is
// deterministic: both threads always reach the gap between check and act.
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicLong;

public class A04_InsecureDesign_DoubleSpendRace {
    interface Account {
        String withdraw(String requestId, long cents, Runnable gap);

        long balance();
    }

    // Noncompliant: read, check, then write back; the gap is a race window
    static final class CheckThenActAccount implements Account {
        private volatile long balance;

        CheckThenActAccount(long balance) {
            this.balance = balance;
        }

        public String withdraw(String requestId, long cents, Runnable gap) {
            long current = balance;
            if (current < cents) return "rejected: insufficient funds";
            gap.run(); // e.g. a fraud check or a call to the payment network
            balance = current - cents; // the second write overwrites the first
            return "paid " + format(cents);
        }

        public long balance() {
            return balance;
        }
    }

    // Compliant: conditional compare-and-set (retry on conflict) plus idempotency keys
    static final class AtomicAccount implements Account {
        private final AtomicLong balance;
        private final Map<String, CompletableFuture<String>> completed = new ConcurrentHashMap<>();

        AtomicAccount(long balance) {
            this.balance = new AtomicLong(balance);
        }

        public String withdraw(String requestId, long cents, Runnable gap) {
            // A replayed request gets the first answer back and moves no money
            CompletableFuture<String> mine = new CompletableFuture<>();
            CompletableFuture<String> first = completed.putIfAbsent(requestId, mine);
            if (first != null) return first.join();
            mine.complete(debit(cents, gap));
            return mine.join();
        }

        private String debit(long cents, Runnable gap) {
            boolean first = true;
            while (true) {
                long current = balance.get();
                if (current < cents) return "rejected: insufficient funds";
                if (first) {
                    gap.run();
                    first = false;
                }
                // Succeeds only if nobody changed the balance since we checked it
                if (balance.compareAndSet(current, current - cents)) return "paid " + format(cents);
            }
        }

        public long balance() {
            return balance.get();
        }
    }

    // Two simultaneous withdrawals of 80.00 from 100.00, both forced to pause after their check
    static void race(String label, Account account) throws InterruptedException {
        CyclicBarrier bothChecked = new CyclicBarrier(2);
        Runnable gap = () -> {
            try {
                bothChecked.await(); // wait until the other request has also read the balance
            } catch (InterruptedException | BrokenBarrierException e) {
                throw new IllegalStateException(e);
            }
        };
        Map<String, String> results = new TreeMap<>();
        List<Thread> threads = new ArrayList<>();
        for (String tab : List.of("tab-1", "tab-2")) {
            threads.add(Thread.ofPlatform().start(() -> {
                String r = account.withdraw("req-" + tab, 80_00, gap);
                synchronized (results) {
                    results.put(tab, r);
                }
            }));
        }
        for (Thread t : threads) t.join();
        System.out.println(label);
        long paid = results.values().stream().filter(r -> r.startsWith("paid")).count();
        System.out.println("  outcomes (sorted): " + results.values().stream().sorted().toList());
        System.out.println("  paid out " + format(paid * 80_00) + " from 100.00, final balance: " + format(account.balance()));
    }

    static String format(long cents) {
        return (cents < 0 ? "-" : "") + Math.abs(cents) / 100 + "." + String.format("%02d", Math.abs(cents) % 100);
    }

    public static void main(String[] args) throws InterruptedException {
        race("noncompliant check-then-act:", new CheckThenActAccount(100_00));
        race("compliant compare-and-set:", new AtomicAccount(100_00));

        // A double-click submits the same request twice; the idempotency key absorbs it
        AtomicAccount account = new AtomicAccount(100_00);
        Runnable noGap = () -> { };
        System.out.println("idempotency key:");
        System.out.println("  first submit:  " + account.withdraw("order-42", 30_00, noGap));
        System.out.println("  resubmit:      " + account.withdraw("order-42", 30_00, noGap) + " (replayed answer, no new debit)");
        System.out.println("  new request:   " + account.withdraw("order-43", 30_00, noGap));
        System.out.println("  final balance: " + format(account.balance()));
    }
}


/* Output:
noncompliant check-then-act:
  outcomes (sorted): [paid 80.00, paid 80.00]
  paid out 160.00 from 100.00, final balance: 20.00
compliant compare-and-set:
  outcomes (sorted): [paid 80.00, rejected: insufficient funds]
  paid out 80.00 from 100.00, final balance: 20.00
idempotency key:
  first submit:  paid 30.00
  resubmit:      paid 30.00 (replayed answer, no new debit)
  new request:   paid 30.00
  final balance: 40.00
*/
