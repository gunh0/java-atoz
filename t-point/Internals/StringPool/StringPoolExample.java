// The string pool (string constant pool) holds one canonical String object per
// distinct literal. Every string literal and every compile-time constant
// expression ("a" + "b", final String constants) refers to the pooled object,
// so equal literals are the same object, even across classes.
// Strings built at runtime (new String, concatenation of non-constant values,
// substring, StringBuilder, reading input) are new heap objects, until you call
// intern(), which returns the pooled copy.
// Takeaways: compare strings with equals(), never ==; == "working" on
// literals is an accident of pooling. intern() is rarely needed and pooled
// strings stay alive, so interning untrusted input is a memory risk.
public class StringPoolExample {
    static final String PREFIX = "role:";      // compile-time constant
    static String dynamicPrefix = "role:";     // not final: not a constant

    static String fromUser() {
        // stands in for input read at runtime
        return new StringBuilder("nimda").reverse().toString();
    }

    public static void main(String[] args) {
        String a = "admin";
        String b = "admin";
        String c = new String("admin");
        String d = "ad" + "min";             // folded by javac into "admin"
        String part = "ad";
        String e = part + "min";             // computed at runtime
        String f = fromUser();

        System.out.println("literal == literal           : " + (a == b));
        System.out.println("literal == new String        : " + (a == c));
        System.out.println("literal == constant concat   : " + (a == d));
        System.out.println("literal == runtime concat    : " + (a == e));
        System.out.println("literal == runtime input     : " + (a == f));
        System.out.println("equals() in every case       : " + (a.equals(c) && a.equals(e) && a.equals(f)));

        System.out.println("new String intern() == lit   : " + (c.intern() == a));
        System.out.println("runtime input intern() == lit: " + (f.intern() == a));

        // final constants take part in constant folding, plain variables do not
        System.out.println("PREFIX + \"admin\" == literal  : " + (PREFIX + "admin" == "role:admin"));
        System.out.println("dynamic + \"admin\" == literal : " + (dynamicPrefix + "admin" == "role:admin"));

        // The classic bug: an authorization check written with ==
        String requestedRole = fromUser();
        System.out.println("== check grants admin        : " + (requestedRole == "admin"));
        System.out.println("equals() check grants admin  : " + "admin".equals(requestedRole));
    }
}

/* Output:
literal == literal           : true
literal == new String        : false
literal == constant concat   : true
literal == runtime concat    : false
literal == runtime input     : false
equals() in every case       : true
new String intern() == lit   : true
runtime input intern() == lit: true
PREFIX + "admin" == literal  : true
dynamic + "admin" == literal : false
== check grants admin        : false
equals() check grants admin  : true
*/
