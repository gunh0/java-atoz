public class UserAgeValidator {
    private int age;

    public UserAgeValidator(int age) {
        setAge(age);
    }

    /**
     * Sets the age, ensuring that it is a valid positive number.
     *
     * @param age Age of the user
     * @throws IllegalArgumentException if the provided age is not a positive number
     */
    public void setAge(int age) {
        if (age <= 0) {
            throw new IllegalArgumentException("Age must be a positive number.");
        }
        this.age = age;
    }

    public int getAge() {
        return age;
    }

    public static void main(String[] args) {
        try {
            UserAgeValidator validator = new UserAgeValidator(-5);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }
    }
}
