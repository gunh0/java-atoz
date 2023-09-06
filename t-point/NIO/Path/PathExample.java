// java.nio.file.Path (NIO.2, Java 7) replaces java.io.File as the way to name
// files. A Path is just a sequence of name elements; creating one never
// touches the disk.
//  - Path.of("a", "b") joins with the platform separator
//  - resolve(other) appends; resolveSibling replaces the last element
//  - normalize() removes "." and "dir/.." purely textually
//  - relativize(other) builds the relative path from one path to another
//  - startsWith works on whole name elements, not characters
// Security note: user input resolved against a base directory must be
// normalized and checked with startsWith, or "../" escapes the directory.
import java.nio.file.Path;

public class PathExample {
    // Resolves a user-supplied name inside baseDir, refusing path traversal
    static Path safeResolve(Path baseDir, String userInput) {
        Path base = baseDir.normalize();
        Path target = base.resolve(userInput).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("path escapes base dir: " + userInput);
        }
        return target;
    }

    public static void main(String[] args) {
        Path log = Path.of("/var", "log", "nginx", "access.log");
        System.out.println("path:        " + log);
        System.out.println("fileName:    " + log.getFileName());
        System.out.println("parent:      " + log.getParent());
        System.out.println("root:        " + log.getRoot());
        System.out.println("nameCount:   " + log.getNameCount() + ", name(1): " + log.getName(1));
        System.out.println("subpath(1,3): " + log.subpath(1, 3));
        System.out.println("isAbsolute:  " + log.isAbsolute() + " / " + Path.of("conf/app.yml").isAbsolute());

        // resolve and resolveSibling
        Path logs = Path.of("/var/log");
        System.out.println("resolve:        " + logs.resolve("auth.log"));
        System.out.println("resolve abs:    " + logs.resolve("/etc/passwd") + "   (an absolute argument wins)");
        System.out.println("resolveSibling: " + log.resolveSibling("error.log"));

        // normalize and relativize
        Path messy = Path.of("/srv/app/./static/../uploads/img.png");
        System.out.println("normalize:   " + messy.normalize());
        System.out.println("relativize:  " + Path.of("/srv/app").relativize(Path.of("/srv/app/uploads/img.png")));
        System.out.println("relativize up: " + Path.of("/srv/app/uploads").relativize(Path.of("/srv/backup")));

        // startsWith compares name elements
        System.out.println("/srv/app2 startsWith /srv/app? " + Path.of("/srv/app2").startsWith(Path.of("/srv/app"))
                + " (String.startsWith: " + "/srv/app2".startsWith("/srv/app") + ")");

        // Path traversal check
        Path uploads = Path.of("/srv/app/uploads");
        for (String name : new String[] {"avatar.png", "2023/09/report.pdf", "../config/db.properties", "../../../etc/shadow"}) {
            try {
                System.out.println("  ok      " + name + " -> " + safeResolve(uploads, name));
            } catch (IllegalArgumentException e) {
                System.out.println("  refused " + name);
            }
        }

        // Converting between Path, File and URI
        System.out.println("toUri: " + Path.of("/tmp/report.txt").toUri());
        System.out.println("toFile().getName(): " + log.toFile().getName());
    }
}

/* Output:
path:        /var/log/nginx/access.log
fileName:    access.log
parent:      /var/log/nginx
root:        /
nameCount:   4, name(1): log
subpath(1,3): log/nginx
isAbsolute:  true / false
resolve:        /var/log/auth.log
resolve abs:    /etc/passwd   (an absolute argument wins)
resolveSibling: /var/log/nginx/error.log
normalize:   /srv/app/uploads/img.png
relativize:  uploads/img.png
relativize up: ../../backup
/srv/app2 startsWith /srv/app? false (String.startsWith: true)
  ok      avatar.png -> /srv/app/uploads/avatar.png
  ok      2023/09/report.pdf -> /srv/app/uploads/2023/09/report.pdf
  refused ../config/db.properties
  refused ../../../etc/shadow
toUri: file:///tmp/report.txt
toFile().getName(): access.log
*/
