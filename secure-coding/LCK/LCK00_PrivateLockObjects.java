// LCK00-J. Use private final lock objects to synchronize classes that may
// interact with untrusted code
// https://wiki.sei.cmu.edu/confluence/display/java/LCK00-J.+Use+private+final+lock+objects+to+synchronize+classes+that+may+interact+with+untrusted+code
//
// A synchronized method locks on "this", and a static synchronized method on
// the Class object. Both are visible to any code holding a reference, so
// untrusted code can write synchronized (account) { sleep forever } and
// every thread that calls the object's methods blocks: a denial of service,
// or deadlock if it acquires locks in a different order. Lock on a private
// final Object instead (the "private lock object" idiom). No one outside the
// class can reach it, and the class's own locking policy stays intact.
import java.util.concurrent.CountDownLatch;

public class LCK00_PrivateLockObjects {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("noncompliant (synchronized methods): " + attack(new AccountNoncompliant()));
        System.out.println("compliant (private lock object):     " + attack(new AccountCompliant()));
    }

    // Untrusted code grabs the object's monitor; then a normal caller tries to deposit
    static String attack(Account account) throws InterruptedException {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread attacker = new Thread(() -> {
            synchronized (account) { // the intrinsic lock of the object
                locked.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        attacker.start();
        locked.await();

        Thread client = new Thread(() -> account.deposit(100));
        client.start();
        client.join(500); // give the deposit plenty of time
        String result = client.isAlive() ? "deposit BLOCKED by outside code" : "deposit completed, balance " + account.balance();

        release.countDown(); // end of the attack
        attacker.join();
        client.join();
        return result;
    }
}

interface Account {
    void deposit(int amount);

    int balance();
}

class AccountNoncompliant implements Account {
    private int balance;

    @Override
    public synchronized void deposit(int amount) { // locks on this
        balance += amount;
    }

    @Override
    public synchronized int balance() {
        return balance;
    }
}

class AccountCompliant implements Account {
    private final Object lock = new Object(); // unreachable from outside
    private int balance;

    @Override
    public void deposit(int amount) {
        synchronized (lock) {
            balance += amount;
        }
    }

    @Override
    public int balance() {
        synchronized (lock) {
            return balance;
        }
    }
}

/* Output:
noncompliant (synchronized methods): deposit BLOCKED by outside code
compliant (private lock object):     deposit completed, balance 100
*/
