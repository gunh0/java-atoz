import java.io.*;

// `java File.java` runs the first class in the file, so the class with main() comes first.
public class ThrowsTest2 {
    public static void main(String args[]) {
        try {
            M m = new M();
            m.method();
        } catch (Exception e) {
            System.out.println("exception handled");
        }
        System.out.println("normal flow...");
    }
}

class M {
    void method() throws IOException {
        throw new IOException("device error");
    }
}

/* Output:
exception handled
normal flow...
*/
