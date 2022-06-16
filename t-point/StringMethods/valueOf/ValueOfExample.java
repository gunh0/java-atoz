// String.valueOf() is a family of static methods that turn any value into its
// string form: primitives, char arrays and objects.
//  - valueOf(Object obj) returns "null" for a null reference instead of
//    throwing, which is why it is safer than obj.toString().
//  - valueOf(char[]) copies the characters; it does NOT call toString() on
//    the array (that would print something like "[C@1b6d3586").
//  - Beware of the null literal: String.valueOf(null) picks the char[]
//    overload (the most specific one) and throws NullPointerException.
//  - The reverse direction lives on the wrapper types: Integer.parseInt(),
//    Double.parseDouble(), Boolean.parseBoolean().
public class ValueOfExample {
    public static void main(String[] args) {
        // Primitives
        System.out.println(String.valueOf(42) + " | " + String.valueOf(3.5) + " | "
                + String.valueOf(true) + " | " + String.valueOf('x') + " | " + String.valueOf(10L));

        // Objects use toString()
        Point p = new Point(3, 4);
        System.out.println(String.valueOf(p));

        // A null reference becomes "null" instead of an NPE
        Point missing = null;
        System.out.println("valueOf(null ref) = " + String.valueOf(missing));
        try {
            System.out.println(missing.toString());
        } catch (NullPointerException e) {
            System.out.println("missing.toString() -> NullPointerException");
        }

        // char[] overloads: the whole array or a range of it
        char[] letters = {'j', 'a', 'v', 'a', '1', '8'};
        System.out.println(String.valueOf(letters));
        System.out.println(String.valueOf(letters, 0, 4));
        System.out.println(String.copyValueOf(letters, 4, 2)); // same thing, older name

        // The null literal trap: it compiles, but resolves to valueOf(char[])
        try {
            String.valueOf(null);
        } catch (NullPointerException e) {
            System.out.println("String.valueOf(null) -> NullPointerException");
        }

        // valueOf vs concatenation vs wrapper toString: same text
        int port = 8080;
        System.out.println(String.valueOf(port).equals("" + port) && Integer.toString(port).equals("" + port));

        // And back again
        int parsed = Integer.parseInt(String.valueOf(port)) + 1;
        System.out.println("next port: " + parsed);
    }

    static class Point {
        private final int x;
        private final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return "Point(" + x + ", " + y + ")";
        }
    }
}

/* Output:
42 | 3.5 | true | x | 10
Point(3, 4)
valueOf(null ref) = null
missing.toString() -> NullPointerException
java18
java
18
String.valueOf(null) -> NullPointerException
true
next port: 8081
*/
