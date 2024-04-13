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

> JEP 330: Launch Single-File Source-Code Programs : <https://openjdk.java.net/jeps/330>

<br/>

<br/>

<br/>

## Sections

<!-- SECTIONS:START -->
| Section | What it is |
|---|---|
| [`t-point`](t-point/) | Java syntax and core APIs, one small program per concept |
| [`effective`](effective/) | Items from *Effective Java, 3rd Edition* |
| [`this-is`](this-is/) | Establishing from the basics: Maven demo projects |
| [`one-day`](one-day/) | A Spring MVC / Tomcat project structure in a day |
| [`modern-java`](modern-java/) | Language and API features by Java release, one JEP per example |
| [`secure-coding`](secure-coding/) | SEI CERT Oracle Coding Standard for Java rules: noncompliant code next to the compliant fix |
| [`patterns`](patterns/) | GoF design patterns, one small realistic scenario per pattern |
| [`crypto`](crypto/) | Java Cryptography Architecture from a security engineer's view: correct use next to classic misuse |
<!-- SECTIONS:END -->

<br/>

## Checking the examples

```bash
make check    # compile every example; run the ones with an /* Output: */ block and compare
```
