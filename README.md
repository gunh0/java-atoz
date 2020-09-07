# Java A to Z

### `t-point`

> .

<br/>

#### Regex
The Java Regex or Regular Expression is an API to define a pattern for searching or manipulating strings.

<br/>

### `this-is`

>.

<br/>

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

<br/>

### How to compile and run in single command in java

Since Java 11 you can use a single command

```java
java example.java
```

> JEP 330: Launch Single-File Source-Code Programs : https://openjdk.java.net/jeps/330