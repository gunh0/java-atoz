// IDS05-J. Use a subset of ASCII for file and path names
// https://wiki.sei.cmu.edu/confluence/display/java/IDS05-J.+Use+a+subset+of+ASCII+for+file+and+path+names
//
// Most file systems accept almost any character in a file name: spaces,
// newlines, a leading "-", shell wildcards, Unicode bidi controls. Java
// will happily create such files, and the damage happens later, in the
// tools that consume them: a script that reads `ls` output line by line,
// a command that takes "-rf" as an option, a viewer that renders
// "invoice‮fdp.exe" as "invoiceexe.pdf". When a name comes from
// untrusted input, allow only a conservative set (letters, digits, '.',
// '_', '-'), forbid a leading '-' or '.', and bound the length.
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class IDS05_AsciiFileNames {
    static final List<String> UPLOADED_NAMES = List.of(
            "report.txt",
            "-rf",
            "two\nlines.txt",
            "invoice‮fdp.exe",   // shows as "invoiceexe.pdf" in bidi-aware UIs
            "*.log",
            " notes.txt");

    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("ids05");
        try {
            // Noncompliant: any name the user sends becomes a file name
            for (String name : UPLOADED_NAMES) {
                Files.writeString(dir.resolve(name), "data");
            }
            // A downstream tool that writes one name per line and reads it back
            String manifest;
            try (Stream<Path> files = Files.list(dir)) {
                manifest = String.join("\n", files.map(p -> p.getFileName().toString()).sorted().toList());
            }
            List<String> lines = manifest.lines().toList();
            System.out.println("noncompliant: created " + UPLOADED_NAMES.size() + " files, manifest has " + lines.size() + " lines");
            for (String line : lines) {
                System.out.println("  " + visible(line) + (Files.exists(dir.resolve(line)) ? "" : "   <- no such file"));
            }

            // Compliant: validate before touching the file system
            System.out.println("compliant:");
            for (String name : UPLOADED_NAMES) {
                System.out.println("  " + String.format("%-22s", visible(name)) + (isSafeName(name) ? "accepted" : "rejected"));
            }
        } finally {
            try (Stream<Path> files = Files.walk(dir)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9._-]{0,63}");

    static boolean isSafeName(String name) {
        return SAFE.matcher(name).matches();
    }

    // Show control and non-ASCII characters as escapes
    static String visible(String s) {
        StringBuilder sb = new StringBuilder();
        s.codePoints().forEach(c -> {
            if (c >= 0x20 && c < 0x7F) {
                sb.appendCodePoint(c);
            } else {
                sb.append(String.format("\\u%04X", c));
            }
        });
        return sb.toString();
    }
}

/* Output:
noncompliant: created 6 files, manifest has 7 lines
   notes.txt
  *.log
  -rf
  invoice\u202Efdp.exe
  report.txt
  two   <- no such file
  lines.txt   <- no such file
compliant:
  report.txt            accepted
  -rf                   rejected
  two\u000Alines.txt    rejected
  invoice\u202Efdp.exe  rejected
  *.log                 rejected
   notes.txt            rejected
*/
