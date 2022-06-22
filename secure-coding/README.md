# secure-coding

> Writing Java that holds up against hostile input.

Runnable notes on the [SEI CERT Oracle Coding Standard for Java](https://wiki.sei.cmu.edu/confluence/display/java/SEI+CERT+Oracle+Coding+Standard+for+Java), the rule set maintained by Carnegie Mellon's Software Engineering Institute. Each rule has an ID made of a category and a number with a `-J` suffix, for example `IDS00-J` (Input Validation and Data Sanitization, rule 0). The categories used here include IDS (input validation), SER (serialization), FIO (file I/O), NUM (numeric types), STR (strings), OBJ (object orientation), ERR (exceptions), LCK (locking), MSC (miscellaneous) and SEC (platform security).

Files live in one directory per category and are named after the rule: `<CATEGORY>/<RULEID>_<ShortName>.java`, e.g. `IDS/IDS00_SqlInjection.java`. Each file is a single-file program that puts the **noncompliant** code and the **compliant** fix side by side and demonstrates the difference in-process: no network, no real attacks, and any files are created in a temporary directory and deleted afterwards. Run one with `java <File>.java` from its directory.

<!-- INDEX:START -->
- MSC02-J, generate strong random numbers: [`MSC/MSC02_SecureRandom.java`](MSC/MSC02_SecureRandom.java)
- IDS00-J, prevent SQL injection: [`IDS/IDS00_SqlInjection.java`](IDS/IDS00_SqlInjection.java)
- FIO16-J, canonicalize path names before validating them: [`FIO/FIO16_CanonicalizePaths.java`](FIO/FIO16_CanonicalizePaths.java)
- SER12-J, prevent deserialization of untrusted data: [`SER/SER12_DeserializationFilter.java`](SER/SER12_DeserializationFilter.java)
- NUM00-J, detect or prevent integer overflow: [`NUM/NUM00_IntegerOverflow.java`](NUM/NUM00_IntegerOverflow.java)
- IDS01-J, normalize strings before validating them: [`IDS/IDS01_NormalizeBeforeValidating.java`](IDS/IDS01_NormalizeBeforeValidating.java)
- EXP00-J, do not ignore values returned by methods: [`EXP/EXP00_IgnoredReturnValues.java`](EXP/EXP00_IgnoredReturnValues.java)
- NUM04-J, do not use floating-point numbers if precise computation is required: [`NUM/NUM04_FloatingPointPrecision.java`](NUM/NUM04_FloatingPointPrecision.java)
<!-- INDEX:END -->
