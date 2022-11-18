// PriorityQueue is a binary heap: poll() always removes the SMALLEST element
// according to the natural ordering or the Comparator given to the constructor.
//  - offer/poll are O(log n), peek is O(1)
//  - toString() and iteration show the heap's internal array, which is NOT
//    sorted; only repeated poll() yields elements in order
//  - for a max-heap pass Comparator.reverseOrder() (or a reversed comparator)
//  - "top K largest" is a min-heap capped at K elements: O(n log K)
// Elements that tie on the comparator come out in no guaranteed order.
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class PriorityQueueExample {
    public static void main(String[] args) {
        PriorityQueue<Integer> heap = new PriorityQueue<>(List.of(42, 7, 19, 3, 25));
        System.out.println("peek(): " + heap.peek());
        System.out.println("internal order (not sorted): " + heap);
        System.out.println("poll order: " + drain(heap));

        PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Comparator.reverseOrder());
        maxHeap.addAll(List.of(42, 7, 19, 3, 25));
        System.out.println("max-heap poll order: " + drain(maxHeap));

        // A ticket queue: severity first (1 = critical), then ticket number
        PriorityQueue<Ticket> tickets = new PriorityQueue<>(
                Comparator.comparingInt(Ticket::severity).thenComparingInt(Ticket::id));
        tickets.offer(new Ticket(101, 3, "typo on login page"));
        tickets.offer(new Ticket(102, 1, "database down"));
        tickets.offer(new Ticket(103, 2, "slow search"));
        tickets.offer(new Ticket(104, 1, "certificate expired"));
        while (!tickets.isEmpty()) {
            Ticket t = tickets.poll();
            System.out.println("handle #" + t.id() + " sev" + t.severity() + " " + t.title());
        }

        // Top 3 largest files with a size-3 min-heap
        int[] sizes = {512, 4096, 128, 65536, 2048, 1024, 32768};
        PriorityQueue<Integer> top = new PriorityQueue<>();
        for (int size : sizes) {
            top.offer(size);
            if (top.size() > 3) {
                top.poll(); // drop the smallest of the current top 4
            }
        }
        List<Integer> largest = drain(top);
        largest.sort(Comparator.reverseOrder());
        System.out.println("top 3 sizes: " + largest);
    }

    private static List<Integer> drain(PriorityQueue<Integer> q) {
        List<Integer> out = new ArrayList<>();
        while (!q.isEmpty()) {
            out.add(q.poll());
        }
        return out;
    }
}

record Ticket(int id, int severity, String title) { }

/* Output:
peek(): 3
internal order (not sorted): [3, 7, 19, 42, 25]
poll order: [3, 7, 19, 25, 42]
max-heap poll order: [42, 25, 19, 7, 3]
handle #102 sev1 database down
handle #104 sev1 certificate expired
handle #103 sev2 slow search
handle #101 sev3 typo on login page
top 3 sizes: [65536, 32768, 4096]
*/
