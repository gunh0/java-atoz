// File attributes in NIO.2 are grouped into views: "basic" (every platform:
// size, type, creation/modification times), "posix" (Unix: owner, group,
// rwx permissions), "dos" (Windows flags), "owner", "acl", "user".
//  - Files.size / isDirectory / isRegularFile / isSymbolicLink for one value
//  - Files.readAttributes(path, BasicFileAttributes.class) reads them in bulk
//  - Files.readAttributes(path, "posix:permissions,size") by name
//  - PosixFilePermissions.fromString("rw-------") and asFileAttribute let you
//    create a file with tight permissions from the start, instead of chmod
//    after the fact (a window in which the file is world-readable)
// Check supportedFileAttributeViews() before using a platform-specific view.
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class AttributesExample {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("attrs");
        Path report = dir.resolve("report.csv");
        Path key = dir.resolve("id_ed25519");
        try {
            Files.writeString(report, "host,port\nweb,443\n");

            // Single attributes
            System.out.println("size=" + Files.size(report) + " regular=" + Files.isRegularFile(report)
                    + " directory=" + Files.isDirectory(report) + " symlink=" + Files.isSymbolicLink(report));
            System.out.println("dir is a directory? " + Files.isDirectory(dir));

            // Bulk read of the basic view
            BasicFileAttributes basic = Files.readAttributes(report, BasicFileAttributes.class);
            System.out.println("basic: size=" + basic.size() + " isRegularFile=" + basic.isRegularFile() + " isOther=" + basic.isOther());

            // Timestamps can be set, e.g. to preserve them when restoring a backup
            Files.setLastModifiedTime(report, FileTime.from(Instant.parse("2023-09-01T00:00:00Z")));
            System.out.println("lastModified: " + Files.getLastModifiedTime(report));

            boolean posix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
            if (posix) {
                // Create a private key file that is never readable by others
                Set<PosixFilePermission> ownerOnly = PosixFilePermissions.fromString("rw-------");
                Files.createFile(key, PosixFilePermissions.asFileAttribute(ownerOnly));
                System.out.println("key permissions: " + PosixFilePermissions.toString(Files.getPosixFilePermissions(key)));

                // Change permissions explicitly
                Files.setPosixFilePermissions(report, PosixFilePermissions.fromString("rw-r-----"));
                System.out.println("report permissions: " + PosixFilePermissions.toString(Files.getPosixFilePermissions(report)));

                // Audit: is anything readable by "others"?
                for (Path p : new Path[] {report, key}) {
                    boolean worldReadable = Files.getPosixFilePermissions(p).contains(PosixFilePermission.OTHERS_READ);
                    System.out.println("  " + p.getFileName() + " world-readable? " + worldReadable);
                }

                // Attributes by name, as a Map
                Map<String, Object> byName = new TreeMap<>(Files.readAttributes(report, "posix:size,permissions"));
                System.out.println("by name: size=" + byName.get("size")
                        + " permissions=" + PosixFilePermissions.toString(castPerms(byName.get("permissions"))));
            } else {
                System.out.println("posix view not supported on this file system");
            }
        } finally {
            Files.deleteIfExists(key);
            Files.deleteIfExists(report);
            Files.deleteIfExists(dir);
        }
    }

    @SuppressWarnings("unchecked")
    static Set<PosixFilePermission> castPerms(Object o) {
        return (Set<PosixFilePermission>) o;
    }
}

/* Output:
size=18 regular=true directory=false symlink=false
dir is a directory? true
basic: size=18 isRegularFile=true isOther=false
lastModified: 2023-09-01T00:00:00Z
key permissions: rw-------
report permissions: rw-r-----
  report.csv world-readable? false
  id_ed25519 world-readable? false
by name: size=18 permissions=rw-r-----
*/
