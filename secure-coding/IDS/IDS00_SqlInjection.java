// IDS00-J. Prevent SQL injection
// https://wiki.sei.cmu.edu/confluence/display/java/IDS00-J.+Prevent+SQL+injection
//
// Building a query by concatenating user input lets the input change the
// query's STRUCTURE: a quote ends the string literal, and whatever follows is
// parsed as SQL. The fix is not escaping by hand but separating code from
// data: use java.sql.PreparedStatement with ? placeholders, so values are
// bound after the statement has been parsed and can never become syntax.
// To stay self-contained (no JDBC driver, no database) this file includes a
// tiny in-memory query engine that understands a WHERE clause with =, AND,
// OR, string literals, ? parameters and -- comments, just like real SQL.
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IDS00_SqlInjection {
    static final List<Map<String, String>> USERS = List.of(
            Map.of("username", "admin", "password", "s3cr3t!", "role", "administrator"),
            Map.of("username", "alice", "password", "wonderland", "role", "user"));

    public static void main(String[] args) {
        String[][] attempts = {
                {"alice", "wonderland"},          // legitimate login
                {"alice", "guess"},               // wrong password
                {"admin' --", "anything"},        // comment out the password check
                {"x' OR 'a'='a' --", "anything"}, // make the condition always true
        };
        for (String[] a : attempts) {
            System.out.printf("user=%-18s noncompliant: %-22s compliant: %s%n",
                    "[" + a[0] + "]", loginNoncompliant(a[0], a[1]), loginCompliant(a[0], a[1]));
        }
    }

    // Noncompliant: user input is pasted into the query text
    static String loginNoncompliant(String username, String password) {
        String sql = "username = '" + username + "' AND password = '" + password + "'";
        return describe(MiniSql.select(USERS, sql, List.of()));
    }

    // Compliant: the query text is constant; values are bound to ? parameters,
    // as with PreparedStatement.setString(1, username)
    static String loginCompliant(String username, String password) {
        String sql = "username = ? AND password = ?";
        return describe(MiniSql.select(USERS, sql, List.of(username, password)));
    }

    static String describe(List<Map<String, String>> rows) {
        if (rows.isEmpty()) {
            return "denied";
        }
        return "logged in as " + rows.get(0).get("username");
    }
}

// A deliberately tiny SQL WHERE-clause engine:
//   expr   := term (OR term)*
//   term   := factor (AND factor)*
//   factor := operand '=' operand
//   operand:= column | 'literal' | ?
// Like a real database, it parses the text first and binds ? values afterwards.
final class MiniSql {
    private final List<String> tokens;
    private final List<String> params;
    private int pos;
    private int paramIndex;

    private MiniSql(List<String> tokens, List<String> params) {
        this.tokens = tokens;
        this.params = params;
    }

    static List<Map<String, String>> select(List<Map<String, String>> table, String where, List<String> params) {
        List<String> tokens = tokenize(where);
        List<Map<String, String>> result = new ArrayList<>();
        for (Map<String, String> row : table) {
            MiniSql parser = new MiniSql(tokens, params);
            if (parser.expr(row)) {
                result.add(row);
            }
        }
        return result;
    }

    private boolean expr(Map<String, String> row) {
        boolean value = term(row);
        while (peek("OR")) {
            pos++;
            value |= term(row);
        }
        return value;
    }

    private boolean term(Map<String, String> row) {
        boolean value = factor(row);
        while (peek("AND")) {
            pos++;
            value &= factor(row);
        }
        return value;
    }

    private boolean factor(Map<String, String> row) {
        String left = operand(row);
        expect("=");
        String right = operand(row);
        return left.equals(right);
    }

    private String operand(Map<String, String> row) {
        String token = tokens.get(pos++);
        if (token.equals("?")) {
            return params.get(paramIndex++);             // bound value: always data
        }
        if (token.startsWith("'")) {
            return token.substring(1);                   // string literal
        }
        return row.getOrDefault(token, "");              // column reference
    }

    private boolean peek(String keyword) {
        return pos < tokens.size() && tokens.get(pos).equalsIgnoreCase(keyword);
    }

    private void expect(String symbol) {
        if (!peek(symbol)) {
            throw new IllegalArgumentException("syntax error near token " + pos);
        }
        pos++;
    }

    // Literals become a token starting with ' ('' inside a literal is an escaped quote)
    private static List<String> tokenize(String sql) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (sql.startsWith("--", i)) {
                break;                                   // comment: ignore the rest
            } else if (c == '=' || c == '?') {
                tokens.add(String.valueOf(c));
                i++;
            } else if (c == '\'') {
                StringBuilder literal = new StringBuilder("'");
                i++;
                while (i < sql.length()) {
                    if (sql.charAt(i) == '\'') {
                        if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                            literal.append('\'');
                            i += 2;
                            continue;
                        }
                        break;
                    }
                    literal.append(sql.charAt(i++));
                }
                i++;                                     // closing quote
                tokens.add(literal.toString());
            } else {
                int start = i;
                while (i < sql.length() && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_')) {
                    i++;
                }
                if (start == i) {
                    throw new IllegalArgumentException("unexpected character: " + c);
                }
                tokens.add(sql.substring(start, i));
            }
        }
        return tokens;
    }
}

/* Output:
user=[alice]            noncompliant: logged in as alice     compliant: logged in as alice
user=[alice]            noncompliant: denied                 compliant: denied
user=[admin' --]        noncompliant: logged in as admin     compliant: denied
user=[x' OR 'a'='a' --] noncompliant: logged in as admin     compliant: denied
*/
