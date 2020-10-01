# Java A to Z

<br/>

### Naming Conventions of the Different Identifiers

| Identifiers Type | Naming Rules                                                 | Examples                                                     |
| :--------------- | :----------------------------------------------------------- | :----------------------------------------------------------- |
| Class            | It should start with the uppercase letter. It should be a noun such as Color, Button, System, Thread, etc. Use appropriate words, instead of acronyms. | public class **Employee** { //code snippet }                 |
| Interface        | It should start with the uppercase letter. It should be an adjective such as Runnable, Remote, ActionListener. Use appropriate words, instead of acronyms. | interface **Printable** { //code snippet }                   |
| Method           | It should start with lowercase letter. It should be a verb such as main(), print(), println(). If the name contains multiple words, start it with a lowercase letter followed by an uppercase letter such as actionPerformed(). | class Employee { // method void **draw()** { //code snippet } } |
| Variable         | It should start with a lowercase letter such as id, name. It should not start with the special characters like & (ampersand), $ (dollar), _ (underscore). If the name contains multiple words, start it with the lowercase letter followed by an uppercase letter such as firstName, lastName. Avoid using one-character variables such as x, y, z. | class Employee { // variable int **id**; //code snippet }    |
| Package          | It should be a lowercase letter such as java, lang. If the name contains multiple words, it should be separated by dots (.) such as java.util, java.lang. | //package package **com.javatpoint;** class Employee { //code snippet } |
| Constant         | It should be in uppercase letters such as RED, YELLOW. If the name contains multiple words, it should be separated by an underscore(_) such as MAX_PRIORITY. It may contain digits but not as the first letter. | class Employee { //constant static final int **MIN_AGE** = 18; //code snippet } |

<br/>

### How to compile and run in single command in java

Since Java 11 you can use a single command

```java
java example.java
```

> JEP 330: Launch Single-File Source-Code Programs : https://openjdk.java.net/jeps/330

<br/>

---

<br/>

### `t-point`

> .

#### 1. Regex

The Java Regex or Regular Expression is an API to define a pattern for searching or manipulating strings.

In Java, the `java.util.regex` package provides support for regular expressions. A regular expression, or regex for short, is a pattern that can be used to match character combinations in strings.

To use the `java.util.regex` package, you must first compile a regular expression pattern into a `Pattern` object. You can then use this `Pattern` object to create a `Matcher` object that can match the pattern against a given string.

<br/>

#### 2. Exceptoin Handling

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

---

<br/>

### `this-is`

>.