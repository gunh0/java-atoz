import java.util.HashSet;
import java.util.Set;

public class Main {
    /**
     * Returns the number of common elements between two sets.
     *
     * @param s1 The first set
     * @param s2 The second set
     * @return The number of common elements
     */
    public static int numElementsInCommon(Set<?> s1, Set<?> s2) {
        // Create a new HashSet to avoid modifying the original sets
        Set<?> tempSet = new HashSet<>(s1);

        // Retain only the elements that are present in both sets
        tempSet.retainAll(s2);
        // Retains only the elements in this set that are contained in the specified collection (optional operation).

        // Print the content of the temporary set
        System.out.println("Temporary set: " + tempSet);

        // Return the size of the resulting set
        return tempSet.size();
    }

    public static void main(String[] args) {
        // Create sets with different types of elements
        Set<String> set1 = new HashSet<>();
        set1.add("apple");
        set1.add("banana");

        Set<String> set2 = new HashSet<>();
        set2.add("banana");
        set2.add("cherry");
        set2.add("date");

        // Print the content of the sets
        System.out.println("Set 1: " + set1);
        System.out.println("Set 2: " + set2);

        // Find the number of common elements
        int numCommonElements = numElementsInCommon(set1, set2);

        // Print the number of common elements
        System.out.println("Number of common elements: " + numCommonElements);
    }
}
