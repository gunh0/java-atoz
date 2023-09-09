// java.nio.file.Files is the utility class that acts on Paths: create, read,
// write, copy, move and delete, with clear exceptions instead of the boolean
// return values of java.io.File.
//  - writeString/readString (Java 11) and write/readAllLines for small files,
//    UTF-8 by default
//  - StandardOpenOption controls writes: CREATE_NEW fails if the file exists,
//    APPEND appends, the default truncates
//  - copy/move refuse to overwrite unless REPLACE_EXISTING is given;
//    ATOMIC_MOVE makes a rename all-or-nothing
//  - delete throws NoSuchFileException, deleteIfExists returns a boolean
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class FilesExample {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("files");
        try {
            Path config = dir.resolve("app.properties");

            // Write and read a whole file
            Files.writeString(config, "debug=false\nport=8443\n");
            System.out.println("exists=" + Files.exists(config) + " size=" + Files.size(config));
            System.out.println("readString: " + Files.readString(config).replace("\n", "\\n"));

            // Append and read as lines
            Files.writeString(config, "tls=true\n", StandardOpenOption.APPEND);
            List<String> lines = Files.readAllLines(config, StandardCharsets.UTF_8);
            System.out.println("readAllLines: " + lines);

            // CREATE_NEW refuses to clobber an existing file
            try {
                Files.writeString(config, "oops", StandardOpenOption.CREATE_NEW);
            } catch (FileAlreadyExistsException e) {
                System.out.println("CREATE_NEW -> FileAlreadyExistsException for " + dir.relativize(Path.of(e.getFile())));
            }

            // Safe config update: write a temp file, then atomically move it into place
            Path tmp = dir.resolve("app.properties.tmp");
            Files.write(tmp, List.of("debug=false", "port=9443", "tls=true"));
            Files.move(tmp, config, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            System.out.println("after atomic replace: " + Files.readAllLines(config) + ", tmp exists=" + Files.exists(tmp));

            // Copy into a subdirectory (createDirectories creates parents, no error if present)
            Path backupDir = Files.createDirectories(dir.resolve("backup/2023-09"));
            Path backup = Files.copy(config, backupDir.resolve("app.properties"));
            System.out.println("backup: " + dir.relativize(backup) + ", identical=" + (Files.mismatch(config, backup) == -1));
            try {
                Files.copy(config, backup);
            } catch (FileAlreadyExistsException e) {
                System.out.println("copy onto existing file -> FileAlreadyExistsException");
            }

            // Bytes
            Path key = dir.resolve("key.bin");
            Files.write(key, new byte[] {1, 2, 3, 4});
            System.out.println("key bytes: " + Files.readAllBytes(key).length);

            // Delete vs deleteIfExists
            Files.delete(key);
            System.out.println("deleteIfExists on missing file: " + Files.deleteIfExists(key));
            try {
                Files.delete(key);
            } catch (NoSuchFileException e) {
                System.out.println("delete on missing file -> NoSuchFileException");
            }

            // Cleanup in reverse order
            Files.delete(backup);
            Files.delete(backupDir);
            Files.delete(backupDir.getParent());
            Files.delete(config);
        } finally {
            Files.deleteIfExists(dir);
        }
    }
}

/* Output:
exists=true size=22
readString: debug=false\nport=8443\n
readAllLines: [debug=false, port=8443, tls=true]
CREATE_NEW -> FileAlreadyExistsException for app.properties
after atomic replace: [debug=false, port=9443, tls=true], tmp exists=false
backup: backup/2023-09/app.properties, identical=true
copy onto existing file -> FileAlreadyExistsException
key bytes: 4
deleteIfExists on missing file: false
delete on missing file -> NoSuchFileException
*/
