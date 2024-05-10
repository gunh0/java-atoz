// FIO01-J. Create files with appropriate access permissions
// https://wiki.sei.cmu.edu/confluence/display/java/FIO01-J.+Create+files+with+appropriate+access+permissions
//
// A new file gets permissions from the process umask unless the program
// asks for something else. With the common umask 022 that is rw-r--r--:
// every local user can read the private key, token cache or export the
// program just wrote. Setting the permissions after creating the file is
// not enough, because between the two calls another process can open it.
// Create sensitive files and directories with restrictive permissions in
// the creating call itself (a FileAttribute on POSIX systems), and prefer
// Files.createTempFile over File.createTempFile for temporary files.
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;

public class FIO01_FilePermissions {
    public static void main(String[] args) throws IOException {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            System.out.println("not a POSIX file system: use ACLs (AclFileAttributeView) instead");
            return;
        }
        Path dir = Files.createTempDirectory("fio01");
        try {
            // Noncompliant: default permissions come from the umask
            Path key = dir.resolve("noncompliant.key");
            Files.writeString(key, "-----BEGIN PRIVATE KEY-----");
            System.out.println("noncompliant Files.writeString:     " + perms(key) + others(key));

            // Noncompliant: restricting afterwards leaves a window
            Path later = dir.resolve("later.key");
            Files.writeString(later, "secret");          // world-readable here...
            Files.setPosixFilePermissions(later, PosixFilePermissions.fromString("rw-------"));
            System.out.println("noncompliant chmod after write:     " + perms(later) + " (but readable in between)");

            // Noncompliant: the old java.io API also uses the umask
            File legacy = File.createTempFile("legacy", ".tmp", dir.toFile());
            System.out.println("noncompliant File.createTempFile:   " + perms(legacy.toPath()) + others(legacy.toPath()));

            // Compliant: permissions are part of the create call (atomic)
            FileAttribute<Set<PosixFilePermission>> ownerOnly =
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));
            Path safe = Files.createFile(dir.resolve("compliant.key"), ownerOnly);
            Files.writeString(safe, "-----BEGIN PRIVATE KEY-----");
            System.out.println("compliant createFile(..., attr):    " + perms(safe) + others(safe));

            Path privateDir = Files.createDirectory(dir.resolve("private"),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            System.out.println("compliant createDirectory(attr):    " + perms(privateDir));

            // Compliant: NIO temp files are owner-only by default
            Path tmp = Files.createTempFile(dir, "nio", ".tmp");
            System.out.println("compliant Files.createTempFile:     " + perms(tmp) + others(tmp));
        } finally {
            try (Stream<Path> files = Files.walk(dir)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    static String perms(Path p) throws IOException {
        return PosixFilePermissions.toString(Files.getPosixFilePermissions(p));
    }

    static String others(Path p) throws IOException {
        boolean readable = Files.getPosixFilePermissions(p).contains(PosixFilePermission.OTHERS_READ);
        return readable ? " (other users can read it)" : " (owner only)";
    }
}

/* Output:
noncompliant Files.writeString:     rw-r--r-- (other users can read it)
noncompliant chmod after write:     rw------- (but readable in between)
noncompliant File.createTempFile:   rw-r--r-- (other users can read it)
compliant createFile(..., attr):    rw------- (owner only)
compliant createDirectory(attr):    rwx------
compliant Files.createTempFile:     rw------- (owner only)
*/
