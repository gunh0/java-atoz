// ProcessBuilder starts an operating-system process. The command is a LIST
// of arguments that goes straight to the program, with no shell in between,
// so characters like ; | $ and spaces in an argument are just data. That is
// the safe way to pass untrusted values to a command; only invoke "sh -c"
// when you really need shell features, and never splice input into it.
//  - read stdout (inputReader() since Java 17) before waitFor(), or a full
//    pipe buffer can block the child forever
//  - redirectErrorStream(true) merges stderr into stdout
//  - environment() is a mutable copy of this JVM's environment for the child
//  - waitFor(timeout, unit) avoids hanging on a stuck child
// Runs on Unix-like systems (uses echo, sh and tr).
import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ProcessBuilderExample {
    record Result(int exitCode, String output) { }

    static Result run(ProcessBuilder pb) throws IOException, InterruptedException {
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out;
        try (BufferedReader r = p.inputReader()) {
            out = r.lines().collect(Collectors.joining("\n"));
        }
        if (!p.waitFor(10, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IllegalStateException("timed out");
        }
        return new Result(p.exitValue(), out);
    }

    public static void main(String[] args) throws Exception {
        // 1. Arguments are passed verbatim: no globbing, no variable expansion, no command chaining
        String untrusted = "hello; echo INJECTED $HOME *";
        Result r = run(new ProcessBuilder("echo", untrusted));
        System.out.println("echo with list args -> " + r.output() + " (exit " + r.exitCode() + ")");

        // 2. Spliced into a shell command line, the same kind of value is interpreted by the shell
        String spliced = "hello; echo INJECTED";
        Result shell = run(new ProcessBuilder("sh", "-c", "echo " + spliced));
        System.out.println("through sh -c       -> " + shell.output().replace("\n", " | "));

        // 3. Passing data to a shell script safely: as a positional parameter, not in the script text
        Result positional = run(new ProcessBuilder("sh", "-c", "echo \"got: $1\"", "sh", untrusted));
        System.out.println("positional param    -> " + positional.output());

        // 4. A controlled environment for the child
        ProcessBuilder envPb = new ProcessBuilder("sh", "-c", "echo \"mode=$APP_MODE secret=${DB_PASSWORD:-unset}\"");
        envPb.environment().put("APP_MODE", "batch");
        envPb.environment().remove("DB_PASSWORD"); // don't pass secrets to children that don't need them
        System.out.println("environment()       -> " + run(envPb).output());

        // 5. Exit codes and stderr merged into stdout
        Result failed = run(new ProcessBuilder("sh", "-c", "echo to-stderr >&2; exit 3"));
        System.out.println("exit code " + failed.exitCode() + ", merged output: " + failed.output());

        // 6. A pipeline: echo ... | tr a-z A-Z
        List<Process> pipeline = ProcessBuilder.startPipeline(List.of(
                new ProcessBuilder("echo", "pipelines need no shell"),
                new ProcessBuilder("tr", "a-z", "A-Z")));
        Process last = pipeline.getLast();
        try (BufferedReader rd = last.inputReader()) {
            System.out.println("pipeline            -> " + rd.readLine());
        }
        last.waitFor(10, TimeUnit.SECONDS);

        // 7. A missing program fails at start(), before any process exists
        try {
            new ProcessBuilder("definitely-not-a-command-xyz").start();
        } catch (IOException e) {
            System.out.println("missing program     -> " + e.getClass().getSimpleName());
        }

        // 8. ProcessHandle describes processes, including this JVM
        ProcessHandle self = ProcessHandle.current();
        System.out.println("this JVM has a pid: " + (self.pid() > 0) + ", alive: " + self.isAlive());
    }
}

/* Output:
echo with list args -> hello; echo INJECTED $HOME * (exit 0)
through sh -c       -> hello | INJECTED
positional param    -> got: hello; echo INJECTED $HOME *
environment()       -> mode=batch secret=unset
exit code 3, merged output: to-stderr
pipeline            -> PIPELINES NEED NO SHELL
missing program     -> IOException
this JVM has a pid: true, alive: true
*/
