// OWASP Top 10 2021: A03:2021 Injection, LDAP filter injection (CWE-90)
// https://owasp.org/Top10/A03_2021-Injection/
//
// LDAP searches take a filter string in the RFC 4515 syntax, such as
// (&(uid=alice)(userPassword=secret)). Building that string by pasting in
// user input lets the input add its own parentheses, operators and
// wildcards: a login filter can be turned into "uid=admin AND (any
// password OR this password)", and a lookup by name into "list every
// entry". Escape the five special characters  * ( ) \ NUL  as \2a \28 \29
// \5c \00 before inserting a value, or better, let the API do it: JNDI's
// DirContext.search(name, "(uid={0})", new Object[]{input}, controls)
// escapes {n} arguments. This program evaluates filters against a small
// in-memory directory, so no LDAP server is involved.
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class A03_Injection_LdapFilter {
    static final List<Map<String, String>> DIRECTORY = List.of(
            Map.of("uid", "admin", "userPassword", "S3cure!pw", "role", "admin"),
            Map.of("uid", "alice", "userPassword", "wonderland", "role", "user"),
            Map.of("uid", "bob", "userPassword", "builder", "role", "user"));

    public static void main(String[] args) {
        // 1. Login: uid and password from a form
        String[][] logins = {
                {"alice", "wonderland"},
                {"alice", "guess"},
                {"admin)(|(userPassword=*", "x)"},
        };
        for (String[] l : logins) {
            String bad = "(&(uid=" + l[0] + ")(userPassword=" + l[1] + "))";
            String good = "(&(uid=" + escape(l[0]) + ")(userPassword=" + escape(l[1]) + "))";
            System.out.println("login uid=\"" + l[0] + "\" password=\"" + l[1] + "\"");
            System.out.println("  noncompliant " + bad + " -> " + search(bad));
            System.out.println("  compliant    " + good + " -> " + search(good));
        }

        // 2. Lookup by name: a wildcard enumerates the directory
        String name = "*";
        System.out.println("lookup name=\"*\"");
        System.out.println("  noncompliant (uid=" + name + ") -> " + search("(uid=" + name + ")"));
        System.out.println("  compliant    (uid=" + escape(name) + ") -> " + search("(uid=" + escape(name) + ")"));
    }

    // RFC 4515 section 3: escape the characters that have meaning in a filter
    static String escape(String value) {
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '*' -> sb.append("\\2a");
                case '(' -> sb.append("\\28");
                case ')' -> sb.append("\\29");
                case '\\' -> sb.append("\\5c");
                case '\0' -> sb.append("\\00");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    static String search(String filter) {
        try {
            Filter f = new FilterParser(filter).parse();
            List<String> hits = new ArrayList<>();
            for (Map<String, String> entry : DIRECTORY) {
                if (f.matches(entry)) hits.add(entry.get("uid"));
            }
            return hits.isEmpty() ? "no match" : "matches " + hits;
        } catch (IllegalArgumentException e) {
            return "invalid filter";
        }
    }

    // A minimal RFC 4515 subset: & | ! and attr=value with * wildcards
    interface Filter {
        boolean matches(Map<String, String> entry);
    }

    static final class FilterParser {
        private final String s;
        private int pos;

        FilterParser(String s) {
            this.s = s;
        }

        Filter parse() {
            Filter f = filter();
            if (pos != s.length()) throw new IllegalArgumentException("trailing data");
            return f;
        }

        private Filter filter() {
            expect('(');
            Filter f;
            char c = peek();
            if (c == '&' || c == '|') {
                pos++;
                List<Filter> parts = new ArrayList<>();
                while (peek() == '(') parts.add(filter());
                f = c == '&'
                        ? e -> parts.stream().allMatch(p -> p.matches(e))
                        : e -> parts.stream().anyMatch(p -> p.matches(e));
            } else if (c == '!') {
                pos++;
                Filter inner = filter();
                f = e -> !inner.matches(e);
            } else {
                int eq = s.indexOf('=', pos);
                int close = s.indexOf(')', pos);
                if (eq < 0 || close < 0 || eq > close) throw new IllegalArgumentException("bad item");
                String attr = s.substring(pos, eq);
                String value = s.substring(eq + 1, close);
                pos = close;
                Pattern p = toPattern(value);
                f = e -> e.containsKey(attr) && p.matcher(e.get(attr)).matches();
            }
            expect(')');
            return f;
        }

        // Unescaped * is a wildcard; \hh is a literal byte
        private static Pattern toPattern(String value) {
            StringBuilder regex = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '*') {
                    regex.append(".*");
                } else if (c == '\\') {
                    if (i + 2 >= value.length()) throw new IllegalArgumentException("bad escape");
                    regex.append(Pattern.quote(String.valueOf((char) Integer.parseInt(value.substring(i + 1, i + 3), 16))));
                    i += 2;
                } else {
                    regex.append(Pattern.quote(String.valueOf(c)));
                }
            }
            return Pattern.compile(regex.toString());
        }

        private char peek() {
            if (pos >= s.length()) throw new IllegalArgumentException("unexpected end");
            return s.charAt(pos);
        }

        private void expect(char c) {
            if (peek() != c) throw new IllegalArgumentException("expected " + c);
            pos++;
        }
    }
}

/* Output:
login uid="alice" password="wonderland"
  noncompliant (&(uid=alice)(userPassword=wonderland)) -> matches [alice]
  compliant    (&(uid=alice)(userPassword=wonderland)) -> matches [alice]
login uid="alice" password="guess"
  noncompliant (&(uid=alice)(userPassword=guess)) -> no match
  compliant    (&(uid=alice)(userPassword=guess)) -> no match
login uid="admin)(|(userPassword=*" password="x)"
  noncompliant (&(uid=admin)(|(userPassword=*)(userPassword=x))) -> matches [admin]
  compliant    (&(uid=admin\29\28|\28userPassword=\2a)(userPassword=x\29)) -> no match
lookup name="*"
  noncompliant (uid=*) -> matches [admin, alice, bob]
  compliant    (uid=\2a) -> no match
*/
