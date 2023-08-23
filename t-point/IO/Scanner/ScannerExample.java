// Scanner splits input into tokens (by whitespace, or a delimiter you set) and
// converts them: nextInt(), nextDouble(), nextBoolean(), next(), nextLine().
//  - hasNextX() checks the type of the next token before you consume it;
//    nextInt() on a non-number throws InputMismatchException
//  - nextInt() leaves the line terminator behind, so a following nextLine()
//    returns "" (the classic Scanner bug)
//  - parsing of numbers is locale-sensitive: useLocale(Locale.ROOT) for data
// Scanner works on a String, a File, a Path or an InputStream; here it reads
// from Strings so the program needs no stdin. For large inputs prefer
// BufferedReader, which is much faster.
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class ScannerExample {
    public static void main(String[] args) {
        // Typed tokens from a config-like string
        try (Scanner sc = new Scanner("maxRetries 3 timeout 2.5 tls true").useLocale(Locale.ROOT)) {
            String k1 = sc.next(); int retries = sc.nextInt();
            String k2 = sc.next(); double timeout = sc.nextDouble();
            String k3 = sc.next(); boolean tls = sc.nextBoolean();
            System.out.println(k1 + "=" + retries + ", " + k2 + "=" + timeout + ", " + k3 + "=" + tls);
        }

        // hasNextInt guards against bad tokens
        List<Integer> ports = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        try (Scanner sc = new Scanner("22 80 http 443 8080x 3389")) {
            while (sc.hasNext()) {
                if (sc.hasNextInt()) ports.add(sc.nextInt());
                else rejected.add(sc.next());
            }
        }
        System.out.println("ports=" + ports + " rejected=" + rejected);

        // Custom delimiter: a CSV line
        try (Scanner sc = new Scanner("10.0.0.5,ssh,denied").useDelimiter(",")) {
            System.out.println("ip=" + sc.next() + " service=" + sc.next() + " action=" + sc.next());
        }

        // Line by line, plus the nextInt()/nextLine() pitfall
        String input = "2\nfirst comment\nsecond comment\n";
        try (Scanner sc = new Scanner(input)) {
            int count = sc.nextInt();
            String rest = sc.nextLine(); // only the "\n" after the 2
            System.out.println("count=" + count + ", leftover nextLine()=\"" + rest + "\"");
            for (int i = 0; i < count; i++) System.out.println("  line: " + sc.nextLine());
        }

        // Locale matters for decimals: "1,5" is a number in Germany, not in ROOT
        System.out.println("hasNextDouble(\"1,5\") GERMANY=" + new Scanner("1,5").useLocale(Locale.GERMANY).hasNextDouble()
                + " ROOT=" + new Scanner("1,5").useLocale(Locale.ROOT).hasNextDouble());

        // findInLine with a regex
        try (Scanner sc = new Scanner("GET /login?user=alice HTTP/1.1")) {
            System.out.println("found: " + sc.findInLine("user=\\w+"));
        }
    }
}

/* Output:
maxRetries=3, timeout=2.5, tls=true
ports=[22, 80, 443, 3389] rejected=[http, 8080x]
ip=10.0.0.5 service=ssh action=denied
count=2, leftover nextLine()=""
  line: first comment
  line: second comment
hasNextDouble("1,5") GERMANY=true ROOT=false
found: user=alice
*/
