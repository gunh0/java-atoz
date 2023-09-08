// LCK07-J. Avoid deadlock by requesting and releasing locks in the same order
// https://wiki.sei.cmu.edu/confluence/display/java/LCK07-J.+Avoid+deadlock+by+requesting+and+releasing+locks+in+the+same+order
//
// A transfer that locks "this account, then the other account" deadlocks as
// soon as two transfers run in opposite directions: thread 1 holds A and
// waits for B while thread 2 holds B and waits for A. An attacker who can
// trigger transfers can freeze the service this way. Acquire multiple locks
// in one global order (here: by account ID) no matter which direction the
// operation goes. The demo uses tryLock() with a timeout so that the
// would-be deadlock is detected and reported instead of hanging, and a
// barrier so that, in the noncompliant run, both threads always hold their
// first lock while they try for the second.
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class LCK07_LockOrdering {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("noncompliant: " + run(false));
        System.out.println("compliant:    " + run(true));
    }

    static String run(boolean ordered) throws InterruptedException {
        Account a = new Account(1, 100);
        Account b = new Account(2, 100);
        CyclicBarrier bothHoldFirst = new CyclicBarrier(2);
        AtomicInteger stuck = new AtomicInteger();

        Thread t1 = new Thread(() -> stuck.addAndGet(transfer(a, b, 10, ordered, bothHoldFirst)));
        Thread t2 = new Thread(() -> stuck.addAndGet(transfer(b, a, 20, ordered, bothHoldFirst)));
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        return stuck.get() == 0
                ? "both transfers done, balances " + a.balance + " / " + b.balance
                : stuck.get() + " of 2 transfers stuck holding one lock and waiting for the other (deadlock)";
    }

    // Returns 0 on success, 1 if the second lock could not be acquired
    static int transfer(Account from, Account to, int amount, boolean ordered, CyclicBarrier barrier) {
        // Noncompliant order: from, then to. Compliant order: lower ID first.
        Account first = from, second = to;
        if (ordered && to.id < from.id) {
            first = to;
            second = from;
        }
        first.lock.lock();
        try {
            if (!ordered) {
                awaitQuietly(barrier); // both threads now hold their first lock
            }
            boolean gotSecond;
            try {
                gotSecond = second.lock.tryLock(300, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                gotSecond = false;
            }
            if (!ordered) {
                awaitQuietly(barrier); // keep holding the first lock until both have tried
            }
            if (!gotSecond) {
                return 1; // with lock() instead of tryLock() this thread would hang forever
            }
            try {
                from.balance -= amount;
                to.balance += amount;
                return 0;
            } finally {
                second.lock.unlock(); // release in the reverse order of acquisition
            }
        } finally {
            first.lock.unlock();
        }
    }

    static void awaitQuietly(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (BrokenBarrierException e) {
            throw new IllegalStateException(e);
        }
    }
}

class Account {
    final int id;
    final ReentrantLock lock = new ReentrantLock();
    int balance;

    Account(int id, int balance) {
        this.id = id;
        this.balance = balance;
    }
}

/* Output:
noncompliant: 2 of 2 transfers stuck holding one lock and waiting for the other (deadlock)
compliant:    both transfers done, balances 110 / 90
*/
