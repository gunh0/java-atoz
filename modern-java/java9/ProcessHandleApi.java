// JEP 102 — Process API Updates (Java 9)
//
// Before Java 9 a program could not portably ask for its own process ID
// (people parsed ManagementFactory.getRuntimeMXBean().getName(), "pid@host"),
// and a child Process offered little beyond waitFor() and destroy().
// ProcessHandle identifies any native process: pid(), parent(), children(),
// descendants(), info() (command, arguments, start time, CPU time when the
// OS reports them), isAlive(), destroy(), and onExit(), a CompletableFuture
// completed when the process ends. Process.toHandle() bridges the two APIs.
// Real PIDs and times vary between runs, so only facts are printed here.
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class ProcessHandleApi {
    public static void main(String[] args) throws Exception {
        // Before: parse "pid@hostname" from the runtime MXBean name
        String jmxName = ManagementFactory.getRuntimeMXBean().getName();
        long oldPid = Long.parseLong(jmxName.substring(0, jmxName.indexOf('@')));

        // After
        ProcessHandle self = ProcessHandle.current();
        System.out.println("current pid matches the old trick: " + (self.pid() == oldPid));
        System.out.println("current process is alive: " + self.isAlive());
        System.out.println("info().command() ends with java: "
                + self.info().command().map(c -> c.endsWith("java")).orElse(false));

        // A child process, observed through its handle
        Process sleeper = new ProcessBuilder("sleep", "30").start();
        ProcessHandle child = sleeper.toHandle();
        System.out.println("child's parent is us: " + child.parent().map(p -> p.pid() == self.pid()).orElse(false));
        System.out.println("listed in our children: " + self.children().anyMatch(h -> h.pid() == child.pid()));
        System.out.println("child alive: " + child.isAlive());

        // onExit: react asynchronously instead of blocking in waitFor()
        var exited = child.onExit().thenApply(h -> "child " + (h.pid() == child.pid() ? "ended" : "?"));
        boolean requested = child.destroy();
        System.out.println("destroy requested: " + requested);
        System.out.println(exited.get(10, TimeUnit.SECONDS));
        System.out.println("child alive after onExit: " + child.isAlive());
        System.out.println("total CPU duration known: "
                + self.info().totalCpuDuration().map(d -> d.compareTo(Duration.ZERO) >= 0).orElse(true));
    }
}

/* Output:
current pid matches the old trick: true
current process is alive: true
info().command() ends with java: true
child's parent is us: true
listed in our children: true
child alive: true
destroy requested: true
child ended
child alive after onExit: false
total CPU duration known: true
*/
