// Effective Java, Item 60: Avoid float and double if exact answers are required.
//
// float and double are binary floating point: they are designed for fast,
// approximate scientific calculation and cannot represent 0.1 or any other
// negative power of ten exactly. That makes them unsuitable for money and
// other values that must come out exact; rounding at the end does not
// always fix the accumulated error. Use BigDecimal (construct it from a
// String or BigDecimal.valueOf, never new BigDecimal(double)) when you need
// exact decimal arithmetic and control over rounding, or int/long counting
// the smallest unit (cents) when the range fits and you can track the
// decimal point yourself.
import java.math.BigDecimal;

public class AvoidFloatForExactAnswers {
    public static void main(String[] args) {
        System.out.println("1.03 - 0.42 = " + (1.03 - 0.42));
        System.out.println("1.00 - 9 * 0.10 = " + (1.00 - 9 * 0.10));

        // You have $1 and buy candies priced 10c, 20c, 30c, ... while you can afford them
        double funds = 1.00;
        int itemsBought = 0;
        for (double price = 0.10; funds >= price; price += 0.10) {
            funds -= price;
            itemsBought++;
        }
        System.out.println("double:     " + itemsBought + " items bought, change $" + funds);

        final BigDecimal TEN_CENTS = new BigDecimal(".10");
        BigDecimal bdFunds = new BigDecimal("1.00");
        itemsBought = 0;
        for (BigDecimal price = TEN_CENTS; bdFunds.compareTo(price) >= 0; price = price.add(TEN_CENTS)) {
            bdFunds = bdFunds.subtract(price);
            itemsBought++;
        }
        System.out.println("BigDecimal: " + itemsBought + " items bought, change $" + bdFunds);

        int cents = 100;
        itemsBought = 0;
        for (int price = 10; cents >= price; price += 10) {
            cents -= price;
            itemsBought++;
        }
        System.out.println("int cents:  " + itemsBought + " items bought, change " + cents + " cents");

        // The BigDecimal(double) constructor inherits the binary error
        System.out.println("new BigDecimal(0.1)       = " + new BigDecimal(0.1));
        System.out.println("BigDecimal.valueOf(0.1)   = " + BigDecimal.valueOf(0.1));
        System.out.println("new BigDecimal(\"0.1\")     = " + new BigDecimal("0.1"));
    }
}

/* Output:
1.03 - 0.42 = 0.6100000000000001
1.00 - 9 * 0.10 = 0.09999999999999998
double:     3 items bought, change $0.3999999999999999
BigDecimal: 4 items bought, change $0.00
int cents:  4 items bought, change 0 cents
new BigDecimal(0.1)       = 0.1000000000000000055511151231257827021181583404541015625
BigDecimal.valueOf(0.1)   = 0.1
new BigDecimal("0.1")     = 0.1
*/
