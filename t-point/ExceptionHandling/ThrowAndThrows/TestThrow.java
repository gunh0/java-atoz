public class TestThrow {
    // defining a method
    public static void checkNum(int num) {
        if (num < 1) {
            // throwing an ArithmeticException with a custom error message
            throw new ArithmeticException("\nNumber is negative, cannot calculate square");
        } else {
            // calculating and printing the square of the number
            System.out.println("Square of " + num + " is " + (num * num));
        }
    }

    // main method
    public static void main(String[] args) {
        TestThrow obj = new TestThrow();
        obj.checkNum(-3); // calling the checkNum method with a negative number
        System.out.println("Rest of the code..");
    }
}
