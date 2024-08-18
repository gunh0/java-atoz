// java.util.Arrays sorts and searches arrays in place:
//  - sort(int[]) uses a dual-pivot quicksort for primitives (not stable,
//    which does not matter for primitives); sort(Object[]) uses TimSort,
//    which is stable, so equal elements keep their relative order
//  - sort(a, from, to) sorts only a half-open range; parallelSort splits
//    big arrays across the common ForkJoinPool (same result)
//  - binarySearch requires a sorted array (sorted with the same comparator);
//    a miss returns -(insertionPoint) - 1, always negative, which tells you
//    where the key would go
import java.util.Arrays;
import java.util.Comparator;

public class SortSearchExample {

    record Employee(String name, String dept, int salary) {
        @Override
        public String toString() { return name + "/" + dept + "/" + salary; }
    }

    public static void main(String[] args) {
        int[] ports = {8080, 22, 443, 3306, 80, 25, 5432};
        Arrays.sort(ports);
        System.out.println("sorted ports: " + Arrays.toString(ports));

        int[] partial = {9, 8, 7, 6, 5, 4, 3};
        Arrays.sort(partial, 2, 5); // only indexes 2, 3, 4
        System.out.println("range [2,5) sorted: " + Arrays.toString(partial));

        // Objects: natural order, a comparator, and stability
        String[] words = {"pear", "Fig", "apple", "banana", "kiwi"};
        Arrays.sort(words);
        System.out.println("natural (uppercase first): " + Arrays.toString(words));
        Arrays.sort(words, String.CASE_INSENSITIVE_ORDER);
        System.out.println("case-insensitive:          " + Arrays.toString(words));
        Arrays.sort(words, Comparator.comparingInt(String::length).thenComparing(Comparator.reverseOrder()));
        System.out.println("by length, then reversed:  " + Arrays.toString(words));

        Employee[] staff = {
                new Employee("dana", "ops", 70), new Employee("ari", "dev", 90),
                new Employee("bo", "ops", 70), new Employee("cy", "dev", 80),
        };
        // Stable: dana stays before bo because both earn 70 and dana came first
        Arrays.sort(staff, Comparator.comparingInt(Employee::salary));
        System.out.println("by salary (stable): " + Arrays.toString(staff));

        // parallelSort gives the same result as sort
        int[] big = new int[100_000];
        Arrays.setAll(big, i -> (int) ((i * 2_654_435_761L) % 100_000));
        int[] copy = big.clone();
        Arrays.sort(big);
        Arrays.parallelSort(copy);
        System.out.println("parallelSort equals sort: " + Arrays.equals(big, copy));

        // binarySearch hits and misses
        System.out.println("search 443:  " + Arrays.binarySearch(ports, 443));
        int miss = Arrays.binarySearch(ports, 100);
        int insertionPoint = -miss - 1;
        System.out.println("search 100:  " + miss + " -> insertion point " + insertionPoint);
        System.out.println("search 9999: " + Arrays.binarySearch(ports, 9999) + " (past the end)");

        // Inserting while keeping the array sorted
        int[] withNew = new int[ports.length + 1];
        System.arraycopy(ports, 0, withNew, 0, insertionPoint);
        withNew[insertionPoint] = 100;
        System.arraycopy(ports, insertionPoint, withNew, insertionPoint + 1, ports.length - insertionPoint);
        System.out.println("after insert: " + Arrays.toString(withNew));

        // Search with the same comparator the array was sorted with
        String[] names = {"Carol", "alice", "Bob", "dave"};
        Arrays.sort(names, String.CASE_INSENSITIVE_ORDER);
        System.out.println("names: " + Arrays.toString(names));
        System.out.println("search \"BOB\" (case-insensitive): " + Arrays.binarySearch(names, "BOB", String.CASE_INSENSITIVE_ORDER));

        // Searching an unsorted array gives an undefined answer, not an error
        int[] unsorted = {5, 1, 4, 2, 3};
        System.out.println("unsorted search for 1 finds it? " + (Arrays.binarySearch(unsorted, 1) >= 0));
    }
}

/* Output:
sorted ports: [22, 25, 80, 443, 3306, 5432, 8080]
range [2,5) sorted: [9, 8, 5, 6, 7, 4, 3]
natural (uppercase first): [Fig, apple, banana, kiwi, pear]
case-insensitive:          [apple, banana, Fig, kiwi, pear]
by length, then reversed:  [Fig, pear, kiwi, apple, banana]
by salary (stable): [dana/ops/70, bo/ops/70, cy/dev/80, ari/dev/90]
parallelSort equals sort: true
search 443:  3
search 100:  -4 -> insertion point 3
search 9999: -8 (past the end)
after insert: [22, 25, 80, 100, 443, 3306, 5432, 8080]
names: [alice, Bob, Carol, dave]
search "BOB" (case-insensitive): 1
unsorted search for 1 finds it? false
*/
