import java.io.*;

// `java File.java` runs the first class in the file, so the class with main() comes first.
class ThrowsTest3 {
    public static void main(String args[]) throws IOException {// declare exception
        M m = new M();
        m.method();

        System.out.println("normal flow...");
    }
}

class M {
    void method() throws IOException {
        System.out.println("device operation performed");
    }
}

/* Output:
device operation performed
normal flow...
*/
