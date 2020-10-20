/* java RegexExFinder.java
 *
 * Enter regex pattern: java
 * Enter text: this is java, do you know java
 * I found the text java starting at index 8 and ending at index 12
 * I found the text java starting at index 26 and ending at index 30
 *
 */

import java.util.regex.Pattern;
import java.util.Scanner;
import java.util.regex.Matcher;

public class RegexExFinder {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println("Enter regex pattern:");
            Pattern pattern = Pattern.compile(sc.nextLine());
            System.out.println("Enter text:");
            Matcher matcher = pattern.matcher(sc.nextLine());
            boolean found = false;
            while (matcher.find()) {
                System.out.println("I found the text " + matcher.group() + " starting at index " +
                        matcher.start() + " and ending at index " + matcher.end());
                found = true;
            }
            if (!found) {
                System.out.println("No match found.");
            }
        }
    }
}
