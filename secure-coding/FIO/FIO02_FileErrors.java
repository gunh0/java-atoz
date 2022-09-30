// FIO02-J. Detect and handle file-related errors
// https://wiki.sei.cmu.edu/confluence/display/java/FIO02-J.+Detect+and+handle+file-related+errors
//
// Many java.io.File methods report failure only by returning false:
// delete(), mkdir(), renameTo(), createNewFile(), setReadOnly() and others.
// Code that ignores the result carries on believing a secret file was
// deleted, a quarantine directory exists, or an upload was moved out of the
// public folder. Check every result, or better, use java.nio.file.Files,
// whose methods throw a specific IOException (NoSuchFileException,
// DirectoryNotEmptyException, FileAlreadyExistsException ...) that says what
// went wrong and cannot be ignored by accident.
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FIO02_FileErrors {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("fio02");
        Path secrets = Files.createDirectories(dir.resolve("secrets"));
        Files.writeString(secrets.resolve("key.pem"), "-----BEGIN KEY-----");
        try {
            // Wipe the secrets directory after use
            System.out.println("noncompliant: " + wipeNoncompliant(secrets.toFile()));
            System.out.println("  key.pem still on disk: " + Files.exists(secrets.resolve("key.pem")));

            System.out.println("compliant:    " + wipeCompliant(secrets));
            System.out.println("  key.pem still on disk: " + Files.exists(secrets.resolve("key.pem")));

            // Move an upload to a directory that does not exist
            Path upload = Files.writeString(dir.resolve("upload.bin"), "data");
            File target = dir.resolve("quarantine").resolve("upload.bin").toFile();
            upload.toFile().renameTo(target); // result ignored
            System.out.println("noncompliant rename: upload still in place: " + Files.exists(upload));
            try {
                Files.move(upload, target.toPath());
            } catch (IOException e) {
                System.out.println("compliant move: " + e.getClass().getSimpleName() + " for "
                        + dir.relativize(Path.of(e.getMessage().split(" -> ")[1])));
            }
        } finally {
            try (var walk = Files.walk(dir)) {
                for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.delete(p);
                }
            }
        }
    }

    // Noncompliant: File.delete() on a non-empty directory just returns false
    static String wipeNoncompliant(File directory) {
        directory.delete();
        return "secrets wiped";
    }

    // Compliant: Files.delete throws, and the failure is handled
    static String wipeCompliant(Path directory) throws IOException {
        try {
            Files.delete(directory);
            return "secrets wiped";
        } catch (java.nio.file.DirectoryNotEmptyException e) {
            try (var files = Files.list(directory)) {
                for (Path p : files.toList()) {
                    Files.delete(p);
                }
            }
            Files.delete(directory);
            return "DirectoryNotEmptyException handled, contents deleted first, secrets wiped";
        }
    }
}

/* Output:
noncompliant: secrets wiped
  key.pem still on disk: true
compliant:    DirectoryNotEmptyException handled, contents deleted first, secrets wiped
  key.pem still on disk: false
noncompliant rename: upload still in place: true
compliant move: NoSuchFileException for quarantine/upload.bin
*/
