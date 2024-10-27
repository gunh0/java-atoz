# dsa

> Data structures and algorithms, implemented from scratch.

Classic data structures and algorithms written in plain, idiomatic Java without leaning on `java.util` for the part being taught: dynamic arrays, linked lists, stacks and queues, hash tables, search trees, heaps, tries, union-find, graph algorithms, sorting, binary search variants, string matching, dynamic programming and bit tricks. Each file explains the idea and its cost (time and space) in its header comment, and its `main` runs a small self-check, usually comparing the hand-written version with the `java.util` equivalent on the same input, then ends with an `/* Output: */` block that `scripts/check.sh` verifies.

Files are named `dsa/<Topic>/<Name>.java`, e.g. `Sorting/MergeSort.java`. Run one with `java <File>.java` from its directory.

<!-- INDEX:START -->
- Arrays, dynamic array with amortized O(1) append by doubling: [`Arrays/DynamicArray.java`](Arrays/DynamicArray.java)
- Linked lists, singly linked list (head/tail, remove, in-place reversal, middle node): [`LinkedLists/SinglyLinkedList.java`](LinkedLists/SinglyLinkedList.java)
- Linked lists, doubly linked list with a sentinel and O(1) unlink: [`LinkedLists/DoublyLinkedList.java`](LinkedLists/DoublyLinkedList.java)
- Stacks and queues, array-backed stack (balanced brackets, RPN evaluation): [`StacksQueues/ArrayStack.java`](StacksQueues/ArrayStack.java)
- Stacks and queues, ring buffer queue with wrap-around and growth: [`StacksQueues/RingBufferQueue.java`](StacksQueues/RingBufferQueue.java)
- Hashing, open addressing hash map (linear probing, tombstones, resize): [`Hashing/OpenAddressingHashMap.java`](Hashing/OpenAddressingHashMap.java)
- Searching, binary search, lower/upper bound and binary search on the answer: [`Searching/BinarySearchBounds.java`](Searching/BinarySearchBounds.java)
- Sorting, merge sort (top-down, bottom-up, stability, inversion count): [`Sorting/MergeSort.java`](Sorting/MergeSort.java)
- Sorting, quicksort with random pivot and 3-way partition: [`Sorting/QuickSort.java`](Sorting/QuickSort.java)
- Sorting, counting sort and LSD radix sort (with negative ints): [`Sorting/CountingRadixSort.java`](Sorting/CountingRadixSort.java)
- Trees, binary search tree (insert, search, three-case delete, height): [`Trees/BinarySearchTree.java`](Trees/BinarySearchTree.java)
- Trees, AVL tree (four rotation cases, balanced on sorted input): [`Trees/AvlTree.java`](Trees/AvlTree.java)
- Heaps, binary min-heap priority queue (sift up/down, O(n) heapify): [`Heaps/BinaryHeap.java`](Heaps/BinaryHeap.java)
- Sorting, in-place heap sort (bottom-up heapify, sift down): [`Sorting/HeapSort.java`](Sorting/HeapSort.java)
- Trees, trie with prefix search, autocomplete and pruning delete: [`Trees/Trie.java`](Trees/Trie.java)
- Graphs, union-find with path compression and union by rank, Kruskal's MST: [`Graphs/UnionFind.java`](Graphs/UnionFind.java)
- Graphs, BFS shortest paths, recursive and iterative DFS, cycle detection: [`Graphs/BfsDfs.java`](Graphs/BfsDfs.java)
- Graphs, Dijkstra with a priority queue and lazy deletion, path reconstruction: [`Graphs/Dijkstra.java`](Graphs/Dijkstra.java)
- Graphs, topological sort with Kahn's algorithm and cycle detection: [`Graphs/TopologicalSort.java`](Graphs/TopologicalSort.java)
- Trees, Fenwick tree (binary indexed tree) for prefix and range sums: [`Trees/FenwickTree.java`](Trees/FenwickTree.java)
- Strings, Knuth-Morris-Pratt search and the failure function: [`Strings/Kmp.java`](Strings/Kmp.java)
<!-- INDEX:END -->
