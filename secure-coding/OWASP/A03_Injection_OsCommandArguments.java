// OWASP Top 10 2021: A03:2021 Injection, OS command ARGUMENT injection (CWE-88)
// https://owasp.org/Top10/A03_2021-Injection/
//
// Passing an argument array to ProcessBuilder keeps a shell out of the
// picture (see IDS07-J), but the program that runs still parses its own
// arguments. A value that starts with "-" is an OPTION, not a file name:
// "sort -o<file>" writes its output to <file>, "tar --checkpoint-action=",
// "git --upload-pack=", "curl -o", "find -exec" and many more turn a
// harmless-looking parameter into a file write or a command. Defences, best
// combined: validate the value against a strict allowlist; put "--" before
// operands so the program stops parsing options; resolve the value to a path
// under the intended base directory and pass that absolute path (it starts
// with "/", so it can never be read as an option). Note in the output that
// "--" alone stops option injection but not "../" traversal.
// The demo runs the real sort program inside a temporary directory whose
// "audit.log" plays the victim. The feature exports the chosen report merged
// with archive.txt: "sort <report> archive.txt". Output shows exit status,
// the sorted lines and whether audit.log is still intact.
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class A03_Injection_OsCommandArguments {
    static final Pattern REPORT_NAME = Pattern.compile("[a-z0-9_]{1,32}\\.txt");
    static Path base;
    static Path reports;
    static Path victim;

    public static void main(String[] args) throws Exception {
        base = Files.createTempDirectory("a03-args");
        reports = Files.createDirectory(base.resolve("reports"));
        victim = base.resolve("audit.log");
        Files.writeString(reports.resolve("q1.txt"), "pear\napple\n");
        Files.writeString(reports.resolve("archive.txt"), "fig\n");
        try {
            for (String input : List.of("q1.txt", "-o../audit.log", "../audit.log")) {
                System.out.println("input " + input);
                attempt("noncompliant  sort <input> archive.txt   ", () -> List.of("sort", input, "archive.txt"));
                attempt("compliant 1   allowlist                  ", () -> REPORT_NAME.matcher(input).matches()
                        ? List.of("sort", input, "archive.txt") : null);
                attempt("compliant 2   sort -- <input> archive.txt", () -> List.of("sort", "--", input, "archive.txt"));
                attempt("compliant 3   resolved under base        ", () -> {
                    Path p = reports.resolve(input).normalize();
                    return p.startsWith(reports) ? List.of("sort", "--", p.toString(), "archive.txt") : null;
                });
            }
        } finally {
            try (Stream<Path> walk = Files.walk(base)) {
                for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(p);
                }
            }
        }
    }

    interface CommandBuilder {
        List<String> build() throws IOException;
    }

    static void attempt(String label, CommandBuilder builder) throws Exception {
        String log = "2025-02-05 login alice ok\n2025-02-05 login mallory failed\n";
        Files.writeString(victim, log);
        List<String> command = builder.build();
        if (command == null) {
            System.out.println("  " + label + ": rejected before running, audit.log intact " + log.equals(Files.readString(victim)));
            return;
        }
        Process p = new ProcessBuilder(new ArrayList<>(command))
                .directory(reports.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD) // its messages vary by platform
                .start();
        p.getOutputStream().close(); // nothing on stdin
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = p.waitFor();
        System.out.println("  " + label + ": " + (exit == 0 ? "exit 0" : "failed")
                + ", stdout " + out.lines().toList() + ", audit.log intact " + log.equals(Files.readString(victim)));
    }
}

/* Output:
input q1.txt
  noncompliant  sort <input> archive.txt   : exit 0, stdout [apple, fig, pear], audit.log intact true
  compliant 1   allowlist                  : exit 0, stdout [apple, fig, pear], audit.log intact true
  compliant 2   sort -- <input> archive.txt: exit 0, stdout [apple, fig, pear], audit.log intact true
  compliant 3   resolved under base        : exit 0, stdout [apple, fig, pear], audit.log intact true
input -o../audit.log
  noncompliant  sort <input> archive.txt   : exit 0, stdout [], audit.log intact false
  compliant 1   allowlist                  : rejected before running, audit.log intact true
  compliant 2   sort -- <input> archive.txt: failed, stdout [], audit.log intact true
  compliant 3   resolved under base        : failed, stdout [], audit.log intact true
input ../audit.log
  noncompliant  sort <input> archive.txt   : exit 0, stdout [2025-02-05 login alice ok, 2025-02-05 login mallory failed, fig], audit.log intact true
  compliant 1   allowlist                  : rejected before running, audit.log intact true
  compliant 2   sort -- <input> archive.txt: exit 0, stdout [2025-02-05 login alice ok, 2025-02-05 login mallory failed, fig], audit.log intact true
  compliant 3   resolved under base        : rejected before running, audit.log intact true
*/
