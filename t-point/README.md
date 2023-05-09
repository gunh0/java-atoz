# t-point

> Acquiring proficiency in Java syntax and constructs.

<br/>

## Examples

Each directory holds single-file programs: run one with `java File.java` from its directory.

<!-- INDEX:START -->
- String `charAt()`: [`StringMethods/charAt`](StringMethods/charAt/)
- String `compareTo()`: [`StringMethods/compareTo`](StringMethods/compareTo/)
- Regex: [`Regex`](Regex/)
- Exceptions: [`ExceptionHandling`](ExceptionHandling/)
- Calling `run()` directly: [`Multithreading/CallingRunMethod`](Multithreading/CallingRunMethod/)
- Joining a thread: [`Multithreading/JoiningAThread`](Multithreading/JoiningAThread/)
- String `concat()`: [`StringMethods/concat`](StringMethods/concat/)
- String `contains()`: [`StringMethods/contains`](StringMethods/contains/)
- String `startsWith()` / `endsWith()`: [`StringMethods/startsWith`](StringMethods/startsWith/)
- String `equals()` / `equalsIgnoreCase()`: [`StringMethods/equals`](StringMethods/equals/)
- String `format()`: [`StringMethods/format`](StringMethods/format/)
- String `indexOf()` / `lastIndexOf()`: [`StringMethods/indexOf`](StringMethods/indexOf/)
- String `join()`: [`StringMethods/join`](StringMethods/join/)
- String `replace()` / `replaceAll()`: [`StringMethods/replace`](StringMethods/replace/)
- String `split()`: [`StringMethods/split`](StringMethods/split/)
- String `substring()`: [`StringMethods/substring`](StringMethods/substring/)
- Sleeping a thread: [`Multithreading/SleepingAThread`](Multithreading/SleepingAThread/)
- Daemon threads: [`Multithreading/DaemonThread`](Multithreading/DaemonThread/)
- Synchronized method: [`Multithreading/SynchronizedMethod`](Multithreading/SynchronizedMethod/)
- Synchronized block: [`Multithreading/SynchronizedBlock`](Multithreading/SynchronizedBlock/)
- `wait()` / `notifyAll()` producer-consumer: [`Multithreading/WaitNotify`](Multithreading/WaitNotify/)
- Interrupting a thread: [`Multithreading/Interrupt`](Multithreading/Interrupt/)
- Thread pool with `ExecutorService`: [`Multithreading/ThreadPool`](Multithreading/ThreadPool/)
- String `toUpperCase()` / `toLowerCase()`: [`StringMethods/toUpperCase`](StringMethods/toUpperCase/)
- String `trim()` vs `strip()`: [`StringMethods/trim`](StringMethods/trim/)
- String `valueOf()`: [`StringMethods/valueOf`](StringMethods/valueOf/)
- String `isEmpty()` / `isBlank()`: [`StringMethods/isEmpty`](StringMethods/isEmpty/)
- `StringBuilder` methods: [`StringBuilder/Methods`](StringBuilder/Methods/)
- `StringBuilder` vs `StringBuffer`: [`StringBuilder/StringBuffer`](StringBuilder/StringBuffer/)
- Thread priority: [`Multithreading/ThreadPriority`](Multithreading/ThreadPriority/)
- OOP, class and object: [`OOP/ClassAndObject`](OOP/ClassAndObject/)
- OOP, constructors, overloading and `this(...)` chaining: [`OOP/Constructors`](OOP/Constructors/)
- OOP, method overloading: [`OOP/MethodOverloading`](OOP/MethodOverloading/)
- OOP, `static` fields, methods and blocks: [`OOP/StaticKeyword`](OOP/StaticKeyword/)
- OOP, the `this` keyword: [`OOP/ThisKeyword`](OOP/ThisKeyword/)
- OOP, inheritance: [`OOP/Inheritance`](OOP/Inheritance/)
- OOP, the `super` keyword: [`OOP/SuperKeyword`](OOP/SuperKeyword/)
- OOP, method overriding: [`OOP/MethodOverriding`](OOP/MethodOverriding/)
- OOP, runtime polymorphism: [`OOP/Polymorphism`](OOP/Polymorphism/)
- OOP, the `final` keyword: [`OOP/FinalKeyword`](OOP/FinalKeyword/)
- OOP, abstract classes: [`OOP/AbstractClass`](OOP/AbstractClass/)
- OOP, interfaces with `default` and `static` methods: [`OOP/Interface`](OOP/Interface/)
- OOP, encapsulation and access modifiers: [`OOP/Encapsulation`](OOP/Encapsulation/)
- OOP, inner and static nested classes: [`OOP/InnerClass`](OOP/InnerClass/)
- OOP, anonymous and local classes: [`OOP/AnonymousClass`](OOP/AnonymousClass/)
- OOP, enums with fields, constructors and methods: [`OOP/Enum`](OOP/Enum/)
- Collections, `ArrayList`: [`Collections/ArrayList`](Collections/ArrayList/)
- Collections, `LinkedList`: [`Collections/LinkedList`](Collections/LinkedList/)
- Collections, `HashSet`: [`Collections/HashSet`](Collections/HashSet/)
- Collections, `LinkedHashSet` and `TreeSet`: [`Collections/TreeSet`](Collections/TreeSet/)
- Collections, `HashMap`: [`Collections/HashMap`](Collections/HashMap/)
- Collections, `LinkedHashMap` (insertion and access order, LRU cache): [`Collections/LinkedHashMap`](Collections/LinkedHashMap/)
- Collections, `TreeMap`: [`Collections/TreeMap`](Collections/TreeMap/)
- Collections, `Iterator` and `ConcurrentModificationException`: [`Collections/Iterator`](Collections/Iterator/)
- Collections, `Comparable` natural ordering: [`Collections/Comparable`](Collections/Comparable/)
- Collections, `Comparator` and its combinators: [`Collections/Comparator`](Collections/Comparator/)
- Collections, `Collections` utility methods: [`Collections/CollectionsUtility`](Collections/CollectionsUtility/)
- Collections, `ArrayDeque` as stack and queue: [`Collections/ArrayDeque`](Collections/ArrayDeque/)
- Collections, `PriorityQueue`: [`Collections/PriorityQueue`](Collections/PriorityQueue/)
- Generics, generic classes: [`Generics/GenericClass`](Generics/GenericClass/)
- Generics, generic methods: [`Generics/GenericMethod`](Generics/GenericMethod/)
- Generics, bounded type parameters: [`Generics/BoundedTypes`](Generics/BoundedTypes/)
- Generics, wildcards (`?`, `extends`, `super`): [`Generics/Wildcards`](Generics/Wildcards/)
- Lambdas, syntax, target typing and variable capture: [`Lambdas/LambdaSyntax`](Lambdas/LambdaSyntax/)
- Lambdas, custom `@FunctionalInterface` types: [`Lambdas/FunctionalInterface`](Lambdas/FunctionalInterface/)
- Lambdas, the `java.util.function` interfaces: [`Lambdas/BuiltInInterfaces`](Lambdas/BuiltInInterfaces/)
- Lambdas, composing `Function`, `Predicate` and `Comparator`: [`Lambdas/Composition`](Lambdas/Composition/)
- Lambdas, the four kinds of method reference: [`Lambdas/MethodReferences`](Lambdas/MethodReferences/)
- `Optional`, creating and unwrapping: [`Optional/Basics`](Optional/Basics/)
- `Optional`, `map` / `flatMap` / `filter` / `or` chains: [`Optional/Chaining`](Optional/Chaining/)
- Streams, creating streams: [`Streams/Creation`](Streams/Creation/)
- Streams, `filter` / `map` and lazy evaluation: [`Streams/FilterMap`](Streams/FilterMap/)
- Streams, `flatMap` and `mapMulti`: [`Streams/FlatMap`](Streams/FlatMap/)
- Streams, `sorted` / `distinct` / `limit` / `skip` / `takeWhile`: [`Streams/Slicing`](Streams/Slicing/)
- Streams, `anyMatch` / `findFirst` / `min` / `max` / `count`: [`Streams/MatchFind`](Streams/MatchFind/)
- Streams, `reduce`: [`Streams/Reduce`](Streams/Reduce/)
- Streams, `toList` / `toSet` / `toCollection` / `joining`: [`Streams/Collectors`](Streams/Collectors/)
- Streams, `Collectors.toMap` and duplicate keys: [`Streams/ToMap`](Streams/ToMap/)
- Streams, `groupingBy` with downstream collectors: [`Streams/GroupingBy`](Streams/GroupingBy/)
- Streams, `partitioningBy`: [`Streams/PartitioningBy`](Streams/PartitioningBy/)
<!-- INDEX:END -->

<br/>

### Regex

The Java Regex or Regular Expression is an API to define a pattern for searching or manipulating strings.

In Java, the `java.util.regex` package provides support for regular expressions. A regular expression, or regex for short, is a pattern that can be used to match character combinations in strings.

To use the `java.util.regex` package, you must first compile a regular expression pattern into a `Pattern` object. You can then use this `Pattern` object to create a `Matcher` object that can match the pattern against a given string.

### Exception Handling

Java provides five keywords that are used to handle the exception. The following table describes each.

`try`

- The "try" keyword is used to specify a block where we should place an exception code. It means we can't use try block alone. The try block must be followed by either catch or finally.

`catch`

- The "catch" block is used to handle the exception. It must be preceded by try block which means we can't use catch block alone. It can be followed by finally block later.

`finally`

- The "finally" block is used to execute the necessary code of the program. It is executed whether an exception is handled or not.

`throw`

- The "throw" keyword is used to throw an exception.

`throws`

- The "throws" keyword is used to declare exceptions. It specifies that there may occur an exception in the method. It doesn't throw an exception. It is always used with method signature.

<br/>

#### Multiple Catch Block

A try block can be followed by one or more catch blocks. Each catch block must contain a different exception handler. So, if you have to perform different tasks at the occurrence of different exceptions, use java multi-catch block.

**Points to remember**

- At a time only one exception occurs and at a time only one catch block is executed.
- All catch blocks must be ordered from most specific to most general, i.e. catch for ArithmeticException must come before catch for Exception.

<br/>

#### Nested try

In Java, using a try block inside another try block is permitted. It is called as nested try block. Every statement that we enter a statement in try block, context of that exception is pushed onto the stack.

For example, the **inner try block** can be used to handle **ArrayIndexOutOfBoundsException** while the **outer try block** can handle the **ArithemeticException** (division by zero).

<br/>

#### Finally Block

**Java finally block** is a block used to execute important code such as closing the connection, etc.

Java finally block is always executed whether an exception is handled or not. Therefore, it contains all the necessary statements that need to be printed regardless of the exception occurs or not.

<br/>

#### Throw Keyword

In Java, exceptions allows us to write good quality codes where the errors are checked at the compile time instead of runtime and we can create custom exceptions making the code recovery and debugging easier.

The Java throw keyword is used to throw an exception explicitly.

We specify the **exception** object which is to be thrown. The Exception has some message with it that provides the error description. These exceptions may be related to user inputs, server, etc.

We can throw either checked or unchecked exceptions in Java by throw keyword. It is mainly used to throw a custom exception.

<br/>

#### Exception Propagation

An exception is first thrown from the top of the stack and if it is not caught, it drops down the call stack to the previous method. If not caught there, the exception again drops down to the previous method, and so on until they are caught or until they reach the very bottom of the call stack. This is called exception propagation.

<br/>

#### Throws Keyword

The **Java throws keyword** is used to declare an exception. It gives an information to the programmer that there may occur an exception. So, it is better for the programmer to provide the exception handling code so that the normal flow of the program can be maintained.

<br/>

#### Difference between throw and throws in Java

| Sr. no. | Basis of Differences    | throw                                                                                                                                 | throws                                                                                                                                                        |
| ------- | ----------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1.      | Definition              | Java `throw` keyword is used to throw an exception explicitly in the code, inside the function or the block of code.                  | Java `throws` keyword is used in the method signature to declare an exception which might be thrown by the function during the execution of the code.         |
| 2.      | Type of exception       | Using `throw` keyword, we can only propagate unchecked exceptions, i.e., checked exceptions cannot be propagated using `throw` alone. | Using `throws` keyword, we can declare both checked and unchecked exceptions. However, the `throws` keyword can be used to propagate checked exceptions only. |
| 3.      | Syntax                  | The `throw` keyword is followed by an instance of Exception to be thrown.                                                             | The `throws` keyword is followed by class names of Exceptions to be thrown.                                                                                   |
| 4.      | Declaration             | `throw` is used within the method.                                                                                                    | `throws` is used with the method signature.                                                                                                                   |
| 5.      | Internal implementation | We are allowed to throw only one exception at a time, i.e., we cannot throw multiple exceptions.                                      | We can declare multiple exceptions using `throws` keyword that can be thrown by the method. For example, `main()` throws `IOException, SQLException`.         |
