// Java 19 — Double.toString and Float.toString return the shortest decimal
// (behaviour fix JDK-4511638, no JEP)
//
// Double.toString is specified to produce the SHORTEST decimal that reads
// back as the same double, but the old algorithm sometimes printed extra
// digits: 2.0E23 came out as 1.9999999999999998E23, 8.41E21 as
// 8.409999999999999E21, 1.0E23 as 9.999999999999999E22. The value was
// right (it parsed back identically) but the text was longer than needed
// and surprised people comparing output, logs and test expectations.
// Java 19 replaced the algorithm (Raffaello Giulietti's Schubfach), so
// toString, String.valueOf and string concatenation now print the shortest
// round-tripping form, and it is also faster. Code that parsed old output
// still works: both strings denote the same double.
import java.math.BigDecimal;

public class DoubleToStringShortest {
    public static void main(String[] args) {
        // What Java 18 and earlier printed for these values (recorded, not computed)
        String[][] cases = {
                {"2.0E23", "1.9999999999999998E23"},
                {"8.41E21", "8.409999999999999E21"},
                {"1.0E23", "9.999999999999999E22"},
                {"2.82879384806159E17", "2.82879384806159008E17"},
                {"4.8726570057E288", "4.8726570056999995E288"},
        };
        for (String[] c : cases) {
            double d = Double.parseDouble(c[0]);
            String now = Double.toString(d);
            // The old text and the new text are the same double ...
            boolean sameValue = Double.parseDouble(c[1]) == d;
            System.out.printf("%-30s now %-20s same double: %b, shorter by %d chars%n",
                    "before " + c[1], now, sameValue, c[1].length() - now.length());
        }

        // ... and the shorter text still round-trips exactly
        double d = 2e23;
        System.out.println("round trip 2e23: " + (Double.parseDouble(Double.toString(d)) == d));

        // String concatenation uses the same algorithm
        System.out.println("concatenation: " + 2e23 + ", " + 8.41e21f);

        // The exact binary value is still available when you need it
        System.out.println("exact value of 2e23: " + new BigDecimal(2e23));
    }
}

/* Output:
before 1.9999999999999998E23   now 2.0E23               same double: true, shorter by 15 chars
before 8.409999999999999E21    now 8.41E21              same double: true, shorter by 13 chars
before 9.999999999999999E22    now 1.0E23               same double: true, shorter by 14 chars
before 2.82879384806159008E17  now 2.82879384806159E17  same double: true, shorter by 3 chars
before 4.8726570056999995E288  now 4.8726570057E288     same double: true, shorter by 6 chars
round trip 2e23: true
concatenation: 2.0E23, 8.41E21
exact value of 2e23: 199999999999999983222784
*/
