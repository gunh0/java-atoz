// Java Memory Model: visibility and the volatile flag
//
// Without synchronization, one thread is not guaranteed to EVER see another
// thread's write: the JIT may keep a field in a register, hoist a read out
// of a loop (while (!ready) {} becomes if (!ready) while (true) {}), and both
// compiler and CPU may reorder independent writes. The JMM (JLS 17.4)
// defines when a write must be visible: a write to a volatile variable
// happens-before every later read of that variable, and that edge carries
// along everything the writer did BEFORE the volatile write. So the
// publication idiom is: write the data (plain fields are fine), then set a
// volatile flag; a reader that sees the flag also sees the data. The broken
// variant (flag not volatile) is not run here, because its failure (a reader
// spinning forever, or stale data) depends on JIT timing; the correct
// variant is guaranteed, so its result is checked across many rounds.
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class VolatileVisibility {
    static final class Mailbox {
        int payload;              // plain field
        String sender;            // plain field
        volatile boolean ready;   // the publication flag
    }

    public static void main(String[] args) throws Exception {
        int rounds = 2_000, stale = 0, completed = 0;
        for (int i = 0; i < rounds; i++) {
            Mailbox box = new Mailbox();
            CountDownLatch go = new CountDownLatch(1);
            int expected = i;

            Thread writer = new Thread(() -> {
                await(go);
                box.payload = expected;   // 1. write the data
                box.sender = "writer";
                box.ready = true;         // 2. volatile write publishes 1.
            });
            int[] seen = new int[1];
            String[] who = new String[1];
            Thread reader = new Thread(() -> {
                await(go);
                while (!box.ready) {      // volatile read: re-read every iteration
                    Thread.onSpinWait();
                }
                seen[0] = box.payload;    // guaranteed to see the writes before ready = true
                who[0] = box.sender;
            });
            reader.start();
            writer.start();
            go.countDown();               // release both at once
            writer.join(5_000);
            reader.join(5_000);
            if (!reader.isAlive()) completed++;
            if (seen[0] != expected || !"writer".equals(who[0])) stale++;
        }
        System.out.println("rounds: " + rounds + ", reader finished every round: " + (completed == rounds));
        System.out.println("rounds where the reader saw stale data: " + stale);

        // volatile gives visibility and ordering, NOT atomicity of compound actions
        System.out.println("note: volatile int x; x++ is still a read and a separate write");
    }

    static void await(CountDownLatch l) {
        try {
            if (!l.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

/* Output:
rounds: 2000, reader finished every round: true
rounds where the reader saw stale data: 0
note: volatile int x; x++ is still a read and a separate write
*/
