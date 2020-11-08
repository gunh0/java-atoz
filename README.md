# Java A to Z

<br/>

### Naming Conventions of the Different Identifiers

| Identifiers Type | Naming Rules                                                                                                                                                                                                                                                                                                                                         | Examples                                                                        |
| :--------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------ |
| Class            | It should start with the uppercase letter. It should be a noun such as Color, Button, System, Thread, etc. Use appropriate words, instead of acronyms.                                                                                                                                                                                               | public class **Employee** { //code snippet }                                    |
| Interface        | It should start with the uppercase letter. It should be an adjective such as Runnable, Remote, ActionListener. Use appropriate words, instead of acronyms.                                                                                                                                                                                           | interface **Printable** { //code snippet }                                      |
| Method           | It should start with lowercase letter. It should be a verb such as main(), print(), println(). If the name contains multiple words, start it with a lowercase letter followed by an uppercase letter such as actionPerformed().                                                                                                                      | class Employee { // method void **draw()** { //code snippet } }                 |
| Variable         | It should start with a lowercase letter such as id, name. It should not start with the special characters like & (ampersand), $ (dollar), \_ (underscore). If the name contains multiple words, start it with the lowercase letter followed by an uppercase letter such as firstName, lastName. Avoid using one-character variables such as x, y, z. | class Employee { // variable int **id**; //code snippet }                       |
| Package          | It should be a lowercase letter such as java, lang. If the name contains multiple words, it should be separated by dots (.) such as java.util, java.lang.                                                                                                                                                                                            | //package package **com.javatpoint;** class Employee { //code snippet }         |
| Constant         | It should be in uppercase letters such as RED, YELLOW. If the name contains multiple words, it should be separated by an underscore(\_) such as MAX_PRIORITY. It may contain digits but not as the first letter.                                                                                                                                     | class Employee { //constant static final int **MIN_AGE** = 18; //code snippet } |

<br/>

### How to compile and run in single command in java

Since Java 11 you can use a single command

```java
java example.java
```

> JEP 330: Launch Single-File Source-Code Programs : https://openjdk.java.net/jeps/330

<br/>

<br/>

<br/>

## `t-point`

### 1. Regex

The Java Regex or Regular Expression is an API to define a pattern for searching or manipulating strings.

In Java, the `java.util.regex` package provides support for regular expressions. A regular expression, or regex for short, is a pattern that can be used to match character combinations in strings.

To use the `java.util.regex` package, you must first compile a regular expression pattern into a `Pattern` object. You can then use this `Pattern` object to create a `Matcher` object that can match the pattern against a given string.

<br/>

### 2. Exceptoin Handling

Java provides five keywords that are used to handle the exception. The following table describes each.

`try`

-   The "try" keyword is used to specify a block where we should place an exception code. It means we can't use try block alone. The try block must be followed by either catch or finally.

`catch`

-   The "catch" block is used to handle the exception. It must be preceded by try block which means we can't use catch block alone. It can be followed by finally block later.

`finally`

-   The "finally" block is used to execute the necessary code of the program. It is executed whether an exception is handled or not.

`throw`

-   The "throw" keyword is used to throw an exception.

`throws`

-   The "throws" keyword is used to declare exceptions. It specifies that there may occur an exception in the method. It doesn't throw an exception. It is always used with method signature.

<br/>

### 3. Multiple Exceptions

A try block can be followed by one or more catch blocks. Each catch block must contain a different exception handler. So, if you have to perform different tasks at the occurrence of different exceptions, use java multi-catch block.

**Points to remember**

-   At a time only one exception occurs and at a time only one catch block is executed.
-   All catch blocks must be ordered from most specific to most general, i.e. catch for ArithmeticException must come before catch for Exception.

<br/>

### 4. Nested try Block

In Java, using a try block inside another try block is permitted. It is called as nested try block. Every statement that we enter a statement in try block, context of that exception is pushed onto the stack.

For example, the **inner try block** can be used to handle **ArrayIndexOutOfBoundsException** while the **outer try block** can handle the **ArithemeticException** (division by zero).

<br/>

### 5. finally Block

**Java finally block** is a block used to execute important code such as closing the connection, etc.

Java finally block is always executed whether an exception is handled or not. Therefore, it contains all the necessary statements that need to be printed regardless of the exception occurs or not.

<br/>

### 6. throw Exception

In Java, exceptions allows us to write good quality codes where the errors are checked at the compile time instead of runtime and we can create custom exceptions making the code recovery and debugging easier.

The Java throw keyword is used to throw an exception explicitly.

We specify the **exception** object which is to be thrown. The Exception has some message with it that provides the error description. These exceptions may be related to user inputs, server, etc.

We can throw either checked or unchecked exceptions in Java by throw keyword. It is mainly used to throw a custom exception.

<br/>

### 7. Exception Propagation

An exception is first thrown from the top of the stack and if it is not caught, it drops down the call stack to the previous method. If not caught there, the exception again drops down to the previous method, and so on until they are caught or until they reach the very bottom of the call stack. This is called exception propagation.

<br/>

### 8. throws Keyword

The **Java throws keyword** is used to declare an exception. It gives an information to the programmer that there may occur an exception. So, it is better for the programmer to provide the exception handling code so that the normal flow of the program can be maintained.

<br/>

<br/>

<br/>

## `this-is`

> .

<br/>

<br/>

<br/>

## `effective`
