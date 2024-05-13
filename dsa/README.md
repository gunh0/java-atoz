# dsa

> Data structures and algorithms, implemented from scratch.

Classic data structures and algorithms written in plain, idiomatic Java without leaning on `java.util` for the part being taught: dynamic arrays, linked lists, stacks and queues, hash tables, search trees, heaps, tries, union-find, graph algorithms, sorting, binary search variants, string matching, dynamic programming and bit tricks. Each file explains the idea and its cost (time and space) in its header comment, and its `main` runs a small self-check, usually comparing the hand-written version with the `java.util` equivalent on the same input, then ends with an `/* Output: */` block that `scripts/check.sh` verifies.

Files are named `dsa/<Topic>/<Name>.java`, e.g. `Sorting/MergeSort.java`. Run one with `java <File>.java` from its directory.

<!-- INDEX:START -->
- Arrays, dynamic array with amortized O(1) append by doubling: [`Arrays/DynamicArray.java`](Arrays/DynamicArray.java)
- Linked lists, singly linked list (head/tail, remove, in-place reversal, middle node): [`LinkedLists/SinglyLinkedList.java`](LinkedLists/SinglyLinkedList.java)
- Linked lists, doubly linked list with a sentinel and O(1) unlink: [`LinkedLists/DoublyLinkedList.java`](LinkedLists/DoublyLinkedList.java)
- Stacks and queues, array-backed stack (balanced brackets, RPN evaluation): [`StacksQueues/ArrayStack.java`](StacksQueues/ArrayStack.java)
<!-- INDEX:END -->
