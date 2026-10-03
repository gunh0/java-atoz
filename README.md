# Java A to Z

<p>
  <a href="https://github.com/gunh0/java-atoz/actions/workflows/check.yml"><img src="https://github.com/gunh0/java-atoz/actions/workflows/check.yml/badge.svg" alt="check"></a>
  <img src="https://img.shields.io/badge/JDK-26-ED8B00?logo=openjdk&logoColor=white" alt="JDK 26">
  <img src="https://img.shields.io/badge/examples-710-2E7D32" alt="710 examples">
</p>

A Java notebook kept since 2020: 710 single-file programs, one concept each, every one runnable with `java File.java`. Almost all of them end with an `/* Output: */` block holding exactly what they print, and CI compiles and runs every example on each push to prove the comments are still true.

It started as syntax notes and grew with the language: *Effective Java*, every Java release from 9 to 26 as it shipped, the SEI CERT secure coding standard and the OWASP Top 10, the Java Cryptography Architecture, JVM internals, data structures and design patterns.

## Sections

<!-- SECTIONS:START -->
| Section | Examples | What it is |
|---|---:|---|
| [`t-point`](t-point/) | 236 | Java syntax and core APIs, one small program per concept |
| [`effective`](effective/) | 92 | Items from *Effective Java, 3rd Edition* |
| [`this-is`](this-is/) | — | Establishing from the basics: Maven demo projects |
| [`one-day`](one-day/) | — | A Spring MVC / Tomcat project structure in a day |
| [`modern-java`](modern-java/) | 85 | Language and API features by Java release, one JEP per example |
| [`secure-coding`](secure-coding/) | 132 | SEI CERT Oracle Coding Standard for Java rules: noncompliant code next to the compliant fix |
| [`patterns`](patterns/) | 23 | GoF design patterns, one small realistic scenario per pattern |
| [`crypto`](crypto/) | 64 | Java Cryptography Architecture from a security engineer's view: correct use next to classic misuse |
| [`jvm`](jvm/) | 38 | How the JVM works, observed from Java: class loading, bytecode, memory model, references, JFR |
| [`dsa`](dsa/) | 40 | Data structures and algorithms implemented from scratch, each checked against `java.util` |
<!-- SECTIONS:END -->

## For security engineers

- **[`secure-coding`](secure-coding/)**: 107 SEI CERT Oracle Coding Standard rules across 17 categories (input validation, serialization, numeric types, locking, file I/O, …) plus 25 [OWASP Top 10 (2021)](secure-coding/OWASP/) examples. Each file puts the noncompliant code next to the compliant fix and demonstrates the difference in-process.
- **[`crypto`](crypto/)**: what each JCA primitive guarantees and how it is misused: digests, MACs and KDFs checked against RFC/NIST vectors, AES mode pitfalls (ECB, CTR malleability, GCM nonce reuse), RSA-OAEP/PSS, ECDSA, X25519, certificate paths, TLS and SPKI pinning, HOTP/TOTP, JWS, post-quantum ML-KEM and ML-DSA, HPKE.
- **[`modern-java`](modern-java/)** security-relevant releases: deserialization filters (17), the KEM API (21), FFM's restricted methods (22), the Security Manager removal (24), the KDF API (25).

## Running and checking

```bash
java crypto/Mac/Totp.java     # run one example (RFC 6238 TOTP test vectors)
make check                    # compile everything, run every /* Output: */ block and compare
make check-docker JDK=21      # the same check on a specific JDK release
```

Conventions used by [`scripts/check.sh`](scripts/check.sh):

- A file is compiled the way `java File.java` sees it (the public class may differ from the file name).
- If it ends with `/* Output: ... */`, it is run and its output must match exactly. Output never depends on timing, hash order, random bytes or JDK-specific messages.
- A file that documents one JDK's behaviour (GC modes, removed options) starts with `// output-jdk: N`; its output is compared only on that release.

Examples in `modern-java/javaNN/` need JDK NN or newer; everything else runs on the JDK named in the badge.

## Notes

### Naming Conventions of the Different Identifiers

| Identifiers Type | Naming Rules                                                                                                                                                                                                                                                                                                                                         | Examples                                                                        |
| :--------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------ |
| Class            | It should start with the uppercase letter. It should be a noun such as Color, Button, System, Thread, etc. Use appropriate words, instead of acronyms.                                                                                                                                                                                               | public class **Employee** { //code snippet }                                    |
| Interface        | It should start with the uppercase letter. It should be an adjective such as Runnable, Remote, ActionListener. Use appropriate words, instead of acronyms.                                                                                                                                                                                           | interface **Printable** { //code snippet }                                      |
| Method           | It should start with lowercase letter. It should be a verb such as main(), print(), println(). If the name contains multiple words, start it with a lowercase letter followed by an uppercase letter such as actionPerformed().                                                                                                                      | class Employee { // method void **draw()** { //code snippet } }                 |
| Variable         | It should start with a lowercase letter such as id, name. It should not start with the special characters like & (ampersand), $ (dollar), \_ (underscore). If the name contains multiple words, start it with the lowercase letter followed by an uppercase letter such as firstName, lastName. Avoid using one-character variables such as x, y, z. | class Employee { // variable int **id**; //code snippet }                       |
| Package          | It should be a lowercase letter such as java, lang. If the name contains multiple words, it should be separated by dots (.) such as java.util, java.lang.                                                                                                                                                                                            | //package package **com.javatpoint;** class Employee { //code snippet }         |
| Constant         | It should be in uppercase letters such as RED, YELLOW. If the name contains multiple words, it should be separated by an underscore(\_) such as MAX_PRIORITY. It may contain digits but not as the first letter.                                                                                                                                     | class Employee { //constant static final int **MIN_AGE** = 18; //code snippet } |

Since Java 11 a single source file runs without a separate compile step (JEP 330), since Java 22 the launcher compiles the other files of the program too (JEP 458), and since Java 25 a file doesn't even need a class (JEP 512, see [`modern-java/java25/CompactSourceFiles.java`](modern-java/java25/CompactSourceFiles.java)).
