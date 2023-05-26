// IntStream, LongStream and DoubleStream hold primitives, so they skip the
// boxing a Stream<Integer> pays for every element, and they add numeric
// terminals: sum(), average(), min()/max() (OptionalInt...), summaryStatistics().
//   from objects:   mapToInt / mapToLong / mapToDouble
//   back to objects: boxed() or mapToObj(f)
//   between them:   asLongStream(), asDoubleStream()
//   sources:        IntStream.range(a, b) (b exclusive), rangeClosed, of,
//                   String.chars(), Random.ints(), Arrays.stream(int[])
import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public class PrimitiveStreamsExample {
    record Upload(String file, int sizeKb) {}

    public static void main(String[] args) {
        // range is a counting for-loop as a stream
        System.out.println("range(0, 5):       " + Arrays.toString(IntStream.range(0, 5).toArray()));
        System.out.println("rangeClosed(1, 5): " + Arrays.toString(IntStream.rangeClosed(1, 5).toArray()));

        List<Upload> uploads = List.of(
                new Upload("report.pdf", 820), new Upload("photo.jpg", 2400), new Upload("notes.txt", 4));
        int totalKb = uploads.stream().mapToInt(Upload::sizeKb).sum();
        OptionalDouble avgKb = uploads.stream().mapToInt(Upload::sizeKb).average();
        System.out.println("total = " + totalKb + " KB, average = " + String.format("%.1f", avgKb.getAsDouble()) + " KB");
        System.out.println("largest = " + uploads.stream().mapToInt(Upload::sizeKb).max().getAsInt() + " KB");
        System.out.println("average of nothing = " + IntStream.empty().average());
        System.out.println(IntStream.of(3, 1, 4, 1, 5).summaryStatistics());

        // Indexed access: range over indices, then look up
        List<String> steps = List.of("recon", "exploit", "persist");
        IntStream.range(0, steps.size())
                .mapToObj(i -> (i + 1) + ". " + steps.get(i))
                .forEach(System.out::println);

        // chars(): a character histogram of a password
        String pw = "Tr0ub4dor&3";
        long digits = pw.chars().filter(Character::isDigit).count();
        long upper = pw.chars().filter(Character::isUpperCase).count();
        long symbols = pw.chars().filter(c -> !Character.isLetterOrDigit(c)).count();
        System.out.println("digits=" + digits + " upper=" + upper + " symbols=" + symbols);
        String reversed = new StringBuilder(pw).reverse().chars()
                .mapToObj(c -> String.valueOf((char) c)).collect(Collectors.joining());
        System.out.println("reversed via chars(): " + reversed);

        // LongStream avoids int overflow: 20! does not fit in an int
        long factorial20 = LongStream.rangeClosed(1, 20).reduce(1, (a, b) -> a * b);
        int overflowed = IntStream.rangeClosed(1, 20).reduce(1, (a, b) -> a * b);
        System.out.println("20! as long = " + factorial20 + ", as int = " + overflowed);

        // boxed() to use a Collector that needs objects
        List<Integer> evens = IntStream.rangeClosed(1, 10).filter(n -> n % 2 == 0).boxed().toList();
        System.out.println("evens: " + evens);

        // DoubleStream from an int stream
        double rms = Math.sqrt(IntStream.of(3, 4).asDoubleStream().map(x -> x * x).average().orElse(0));
        System.out.printf("root mean square of 3, 4 = %.4f%n", rms);
    }
}

/* Output:
range(0, 5):       [0, 1, 2, 3, 4]
rangeClosed(1, 5): [1, 2, 3, 4, 5]
total = 3224 KB, average = 1074.7 KB
largest = 2400 KB
average of nothing = OptionalDouble.empty
IntSummaryStatistics{count=5, sum=14, min=1, average=2.800000, max=5}
1. recon
2. exploit
3. persist
digits=3 upper=1 symbols=1
reversed via chars(): 3&rod4bu0rT
20! as long = 2432902008176640000, as int = -2102132736
evens: [2, 4, 6, 8, 10]
root mean square of 3, 4 = 3.5355
*/
