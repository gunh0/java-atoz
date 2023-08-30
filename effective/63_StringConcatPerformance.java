// Effective Java, Item 63: Beware the performance of string concatenation.
//
// Strings are immutable, so s += x creates a new String and copies both
// operands into it. Doing that in a loop copies the growing result again on
// every iteration: building n pieces costs O(n^2) character copies. A single
// expression like a + b + c is fine (javac compiles it to one efficient
// concatenation), but to assemble a result piece by piece use a
// StringBuilder, ideally pre-sized, which appends in amortised O(1) per
// character. This demo does not time anything; it counts the characters
// each approach has to copy, which is what dominates the run time.
public class StringConcatPerformance {
    static final int LINE_WIDTH = 80;

    public static void main(String[] args) {
        for (int items : new int[] { 10, 100, 1_000 }) {
            Copies naive = concatNaive(items);
            Copies builder = concatBuilder(items);
            System.out.printf("%,5d lines: += copies %,12d chars, StringBuilder copies %,8d chars, same result: %b%n",
                    items, naive.copied, builder.copied, naive.result.equals(builder.result));
        }
        System.out.println("10x more lines -> ~100x more copying with +=, 10x with StringBuilder");
    }

    record Copies(String result, long copied) {
    }

    // Each += allocates a new string of the combined length and copies everything into it
    static Copies concatNaive(int numItems) {
        String result = "";
        long copied = 0;
        for (int i = 0; i < numItems; i++) {
            String line = lineForItem(i);
            result += line;
            copied += result.length();
        }
        return new Copies(result, copied);
    }

    // A pre-sized builder copies each character once
    static Copies concatBuilder(int numItems) {
        StringBuilder b = new StringBuilder(numItems * LINE_WIDTH);
        long copied = 0;
        for (int i = 0; i < numItems; i++) {
            String line = lineForItem(i);
            b.append(line);
            copied += line.length();
        }
        return new Copies(b.toString(), copied);
    }

    static String lineForItem(int i) {
        String prefix = "item " + i + ": "; // one expression: fine
        return prefix + ".".repeat(LINE_WIDTH - prefix.length());
    }
}

/* Output:
   10 lines: += copies        4,400 chars, StringBuilder copies      800 chars, same result: true
  100 lines: += copies      404,000 chars, StringBuilder copies    8,000 chars, same result: true
1,000 lines: += copies   40,040,000 chars, StringBuilder copies   80,000 chars, same result: true
10x more lines -> ~100x more copying with +=, 10x with StringBuilder
*/
