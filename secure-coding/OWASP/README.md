# OWASP

Runnable notes on web-application risks from the [OWASP Top 10 2021](https://owasp.org/Top10/), seen from Java. The CERT rules in the other folders are about the language and its libraries; these files are about the application layer: what a server does with a filter string, a redirect target, a cookie, a token or a URL it was asked to fetch.

Files are named after the category they belong to, `A<NN>_<Category>_<Topic>.java`, e.g. `A03_Injection_LdapFilter.java` for A03:2021 Injection. Like the rest of this section, every file is a single-file program that shows the vulnerable pattern next to the fix and demonstrates the difference in-process: no network beyond a loopback test server, no real targets, nothing that would work as an attack tool.
