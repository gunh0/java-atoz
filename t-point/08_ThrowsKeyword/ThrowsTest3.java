import java.io.*;

class M {
    void method() throws IOException {
        System.out.println("device operation performed");
    }
}

class ThrowsTest3 {
    public static void main(String args[]) throws IOException {// declare exception
        M m = new M();
        m.method();

        System.out.println("normal flow...");
    }
}