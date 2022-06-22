// NUM04-J. Do not use floating-point numbers if precise computation is
// required
// https://wiki.sei.cmu.edu/confluence/display/java/NUM04-J.+Do+not+use+floating-point+numbers+if+precise+computation+is+required
//
// float and double are binary fractions: most decimal values, including
// 0.10, have no exact representation, so every operation rounds and the
// errors accumulate. That is fine for measurements, but not for money,
// counters or anything compared for exact equality, where a missing cent
// can let a check pass or fail incorrectly. Use integer types for amounts in
// the smallest unit (cents, won), or BigDecimal created from a String or
// BigDecimal.valueOf, never new BigDecimal(double).
import java.math.BigDecimal;

public class NUM04_FloatingPointPrecision {
    public static void main(String[] args) {
        // Buy as many 10-cent items as possible with one dollar
        System.out.println("noncompliant double: " + buyItemsNoncompliant());
        System.out.println("compliant cents:     " + buyItemsCents());
        System.out.println("compliant BigDecimal: " + buyItemsBigDecimal());

        // Exact comparisons on doubles fail in surprising places
        double total = 0.1 + 0.2;
        System.out.println("0.1 + 0.2 == 0.3: " + (total == 0.3) + " (" + total + ")");
        System.out.println("BigDecimal 0.1 + 0.2 equals 0.3: "
                + new BigDecimal("0.1").add(new BigDecimal("0.2")).equals(new BigDecimal("0.3")));

        // new BigDecimal(double) faithfully keeps the binary error
        System.out.println("new BigDecimal(0.1):     " + new BigDecimal(0.1));
        System.out.println("BigDecimal.valueOf(0.1): " + BigDecimal.valueOf(0.1));
    }

    // Noncompliant: the remaining funds drift away from the decimal value
    static String buyItemsNoncompliant() {
        double funds = 1.00;
        int itemsBought = 0;
        for (double price = 0.10; funds >= price; price += 0.10) {
            funds -= price;
            itemsBought++;
        }
        return itemsBought + " items bought, change " + funds;
    }

    // Compliant: integer cents
    static String buyItemsCents() {
        int funds = 100;
        int itemsBought = 0;
        for (int price = 10; funds >= price; price += 10) {
            funds -= price;
            itemsBought++;
        }
        return itemsBought + " items bought, change " + funds + " cents";
    }

    // Compliant: BigDecimal from string literals
    static String buyItemsBigDecimal() {
        BigDecimal tenCents = new BigDecimal("0.10");
        BigDecimal funds = new BigDecimal("1.00");
        int itemsBought = 0;
        for (BigDecimal price = tenCents; funds.compareTo(price) >= 0; price = price.add(tenCents)) {
            funds = funds.subtract(price);
            itemsBought++;
        }
        return itemsBought + " items bought, change " + funds;
    }
}

/* Output:
noncompliant double: 3 items bought, change 0.3999999999999999
compliant cents:     4 items bought, change 0 cents
compliant BigDecimal: 4 items bought, change 0.00
0.1 + 0.2 == 0.3: false (0.30000000000000004)
BigDecimal 0.1 + 0.2 equals 0.3: true
new BigDecimal(0.1):     0.1000000000000000055511151231257827021181583404541015625
BigDecimal.valueOf(0.1): 0.1
*/
