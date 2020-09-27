/*
 * Exception in thread "main" java.lang.ArithmeticException: / by zero
        at ExceptionDemo.main(ExceptionDemo.java:3)
 */

public class ExceptionDemo {
    public static void main(String[] args) {
        int data = 50 / 0; // may throw exception
        System.out.println("rest of the code");
    }
}
