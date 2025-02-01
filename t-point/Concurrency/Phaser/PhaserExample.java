// A Phaser is a reusable barrier whose number of parties can change while it
// runs, which CountDownLatch (one-shot, fixed count) and CyclicBarrier (fixed
// count) cannot do:
//  - register() / bulkRegister(n) add parties, arriveAndDeregister() removes one
//  - arriveAndAwaitAdvance() waits until every registered party has arrived,
//    then the phase number advances (0, 1, 2, ...)
//  - arrive() signals without waiting; awaitAdvance(phase) waits without
//    arriving (an observer)
//  - onAdvance(phase, parties) runs once per phase in the last arriving
//    thread; returning true terminates the phaser
// Below, three workers go through download -> verify -> install. One fails
// verification and deregisters, and the others go on without waiting for it.
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;

public class PhaserExample {
    static final List<String> STAGES = List.of("download", "verify", "install");
    static final ConcurrentSkipListSet<String> log = new ConcurrentSkipListSet<>();

    public static void main(String[] args) throws Exception {
        Phaser phaser = new Phaser(1) { // party 1 is main, the coordinator
            @Override
            protected boolean onAdvance(int phase, int registeredParties) {
                // Single-threaded here: the last arriver runs it before anyone moves on
                System.out.println("phase " + phase + " (" + STAGES.get(phase) + ") complete, "
                        + registeredParties + " parties, done: " + log.stream()
                                .filter(s -> s.startsWith(phase + " ")).map(s -> s.substring(2)).toList());
                return phase == STAGES.size() - 1 || registeredParties == 0;
            }
        };

        List<String> packages = List.of("core", "net", "ui");
        try (ExecutorService pool = Executors.newFixedThreadPool(packages.size())) {
            for (String pkg : packages) {
                phaser.register(); // a new party joins before its task starts
                pool.submit(() -> {
                    for (int phase = 0; phase < STAGES.size(); phase++) {
                        if (STAGES.get(phase).equals("verify") && pkg.equals("net")) {
                            log.add(phase + " " + pkg + " FAILED");
                            phaser.arriveAndDeregister(); // leave; nobody waits for us any more
                            return;
                        }
                        log.add(phase + " " + pkg);
                        phaser.arriveAndAwaitAdvance();
                    }
                });
            }
            // main takes part as an observer: arrive without waiting, every phase
            while (!phaser.isTerminated()) {
                int phase = phaser.arrive();
                phaser.awaitAdvance(phase);
            }
        }
        System.out.println("terminated: " + phaser.isTerminated() + ", registered parties left: "
                + phaser.getRegisteredParties());

        // bulkRegister: main keeps one party, each round adds 4 short-lived ones
        Phaser rounds = new Phaser(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            for (int round = 0; round < 3; round++) {
                rounds.bulkRegister(4);
                for (int i = 0; i < 4; i++) {
                    pool.submit(rounds::arriveAndDeregister);
                }
                rounds.arriveAndAwaitAdvance(); // main waits for the 4 tasks of this round
                System.out.println("round " + round + " done, phase now " + rounds.getPhase()
                        + ", parties " + rounds.getRegisteredParties());
            }
        }
        rounds.arriveAndDeregister(); // main leaves too: zero parties terminates the phaser
        System.out.println("rounds terminated: " + rounds.isTerminated());
    }
}

/* Output:
phase 0 (download) complete, 4 parties, done: [core, net, ui]
phase 1 (verify) complete, 3 parties, done: [core, net FAILED, ui]
phase 2 (install) complete, 3 parties, done: [core, ui]
terminated: true, registered parties left: 3
round 0 done, phase now 1, parties 1
round 1 done, phase now 2, parties 1
round 2 done, phase now 3, parties 1
rounds terminated: true
*/
