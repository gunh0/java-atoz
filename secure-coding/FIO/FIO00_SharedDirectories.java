// FIO00-J. Do not operate on files in shared directories
// https://wiki.sei.cmu.edu/confluence/display/java/FIO00-J.+Do+not+operate+on+files+in+shared+directories
//
// In a directory that other users can write to (/tmp, a shared upload or
// spool folder), any name you plan to use may already exist, created by
// someone else: as a symbolic link to a file you can write but they cannot
// (your config, your SSH keys), or swapped in between your check and your
// use (time-of-check to time-of-use). Files.write with default options
// follows the link and overwrites the target with your privileges.
// Prefer a private directory that only your user can access (owner-only
// permissions, created atomically). If you must use a shared one, create
// files atomically with CREATE_NEW, which fails if anything, including a
// symlink, already has the name, and do not follow links (NOFOLLOW_LINKS).
// The "shared directory" and the victim file are inside a temp directory.
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.stream.Stream;

public class FIO00_SharedDirectories {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("fio00");
        try {
            Path shared = Files.createDirectory(root.resolve("shared"));
            Path victim = Files.writeString(Files.createDirectory(root.resolve("home")).resolve("settings.conf"),
                    "admin=false\n");

            // The attacker guesses the name the program will use and plants a link
            Path report = shared.resolve("report.txt");
            Files.createSymbolicLink(report, Path.of("..", "home", "settings.conf"));
            System.out.println("planted: report.txt is a symlink: " + Files.isSymbolicLink(report));

            // Noncompliant: default options follow the link and truncate the target
            Files.writeString(report, "admin=true\n");
            System.out.println("noncompliant write -> victim settings.conf now: " + Files.readString(victim).strip());

            // Compliant 1: atomic create, never follow links
            Files.writeString(victim, "admin=false\n");
            try (OutputStream out = Files.newOutputStream(report,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                out.write("admin=true\n".getBytes(StandardCharsets.UTF_8));
                System.out.println("compliant 1 wrote the report");
            } catch (FileAlreadyExistsException e) {
                System.out.println("compliant 1 CREATE_NEW + NOFOLLOW_LINKS -> " + e.getClass().getSimpleName()
                        + ", victim still: " + Files.readString(victim).strip());
            }

            // Compliant 2: a private, randomly named directory created atomically with owner-only access
            Path privateDir = Files.createTempDirectory(shared, "app-",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            Path safeReport = Files.writeString(privateDir.resolve("report.txt"), "quarterly numbers\n",
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            System.out.println("compliant 2 private dir permissions: "
                    + PosixFilePermissions.toString(Files.getPosixFilePermissions(privateDir))
                    + ", report is a regular file (not a link): "
                    + Files.isRegularFile(safeReport, LinkOption.NOFOLLOW_LINKS));
        } finally {
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(p);
                }
            }
        }
    }
}

/* Output:
planted: report.txt is a symlink: true
noncompliant write -> victim settings.conf now: admin=true
compliant 1 CREATE_NEW + NOFOLLOW_LINKS -> FileAlreadyExistsException, victim still: admin=false
compliant 2 private dir permissions: rwx------, report is a regular file (not a link): true
*/
