// String.concat() appends another string and returns a NEW String.
// Strings are immutable, so the receiver itself never changes.
// Unlike the + operator, concat() only accepts a String: it throws
// NullPointerException for null instead of appending the text "null",
// and it does not convert numbers or other objects for you.
public class ConcatExample {
    public static void main(String[] args) {
        String greeting = "Hello";
        String result = greeting.concat(", World");
        System.out.println(result);
        System.out.println(greeting); // still "Hello": concat() returned a new object

        // Each call returns a String, so calls can be chained
        String path = "usr".concat("/").concat("local").concat("/").concat("bin");
        System.out.println(path);

        // Concatenating an empty string returns the very same instance
        System.out.println("same instance for concat(\"\"): " + (greeting.concat("") == greeting));

        // + converts anything (even null) to text; concat() does not
        String missing = null;
        System.out.println("with +: " + missing + " / " + 42);
        try {
            "with concat: ".concat(missing);
        } catch (NullPointerException e) {
            System.out.println("concat(null) throws NullPointerException");
        }
    }
}

/* Output:
Hello, World
Hello
usr/local/bin
same instance for concat(""): true
with +: null / 42
concat(null) throws NullPointerException
*/
