// StringBuilder is a MUTABLE sequence of characters. Where every String
// operation returns a new String, StringBuilder edits one internal buffer in
// place and returns `this`, so calls can be chained.
//  - append / insert / delete / deleteCharAt / replace / reverse / setCharAt
//  - length() is the number of chars in use, capacity() the buffer size;
//    the buffer grows automatically (roughly doubling) when it fills up
//  - building a string in a loop with += creates a new String per iteration;
//    a StringBuilder does the same job with one growing buffer
//  - StringBuilder does not override equals(): compare via toString() or
//    compareTo() (Java 11)
public class StringBuilderExample {
    public static void main(String[] args) {
        StringBuilder sb = new StringBuilder("Java");
        sb.append(' ').append(18).append(" is out"); // append any type, chained
        System.out.println(sb);

        sb.insert(0, "Hello, ");          // insert at an index
        System.out.println(sb);

        sb.replace(7, 11, "JDK");         // replace the range [7, 11)
        System.out.println(sb);

        sb.delete(sb.indexOf(" is"), sb.length()); // delete the range [start, end)
        System.out.println(sb);

        sb.deleteCharAt(5);               // remove the comma
        sb.setCharAt(0, 'h');
        System.out.println(sb);

        System.out.println(new StringBuilder("stressed").reverse());

        // length vs capacity: default capacity is 16, grows as needed
        StringBuilder buf = new StringBuilder();
        System.out.println("empty: length=" + buf.length() + " capacity=" + buf.capacity());
        buf.append("0123456789abcdefg"); // 17 chars, more than 16
        System.out.println("17 chars: length=" + buf.length() + " capacity=" + buf.capacity());
        buf.setLength(4);                 // truncate
        System.out.println("setLength(4): " + buf);

        // The typical use: building output in a loop
        String[] hosts = {"web-1", "web-2", "db-1"};
        StringBuilder csv = new StringBuilder();
        for (String host : hosts) {
            if (csv.length() > 0) csv.append(',');
            csv.append(host);
        }
        System.out.println(csv);

        // equals() is identity for StringBuilder
        StringBuilder a = new StringBuilder("same");
        StringBuilder b = new StringBuilder("same");
        System.out.println("a.equals(b) = " + a.equals(b));
        System.out.println("a.toString().equals(b.toString()) = " + a.toString().equals(b.toString()));
        System.out.println("a.compareTo(b) = " + a.compareTo(b));
    }
}

/* Output:
Java 18 is out
Hello, Java 18 is out
Hello, JDK 18 is out
Hello, JDK 18
hello JDK 18
desserts
empty: length=0 capacity=16
17 chars: length=17 capacity=34
setLength(4): 0123
web-1,web-2,db-1
a.equals(b) = false
a.toString().equals(b.toString()) = true
a.compareTo(b) = 0
*/
