// Interpreter (behavioral): given a language, define a representation for its
// grammar along with an interpreter that uses the representation to
// interpret sentences in the language.
//
// Here the language is a tiny access-rule DSL, e.g.
//   port == 22 and not (src == "10.0.0.5" or user == "root")
// Each grammar rule is a class: terminal expressions (Compare) read a value
// from the context, nonterminal ones (And, Or, Not) combine sub-expressions.
// A sentence becomes an abstract syntax tree of these objects, and
// evaluating it is just calling interpret(context) on the root. A small
// recursive-descent parser builds the tree; parsing is not part of the
// pattern itself, but a rule DSL needs one.
// JDK examples: java.util.regex.Pattern compiles a regex into a tree of node
// objects that match input, and java.text.Format/MessageFormat do the same
// for format patterns.
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InterpreterDemo {
    // The abstract expression
    interface Expr {
        boolean interpret(Map<String, String> context);
    }

    // Terminal expression: <field> <op> <literal>
    record Compare(String field, String op, String literal) implements Expr {
        public boolean interpret(Map<String, String> ctx) {
            String value = ctx.get(field);
            if (value == null) return false; // unknown fields never match
            return switch (op) {
                case "==" -> value.equals(literal);
                case "!=" -> !value.equals(literal);
                case ">" -> Integer.parseInt(value) > Integer.parseInt(literal);
                case "<" -> Integer.parseInt(value) < Integer.parseInt(literal);
                default -> throw new IllegalStateException(op);
            };
        }

        @Override public String toString() { return field + op + literal; }
    }

    // Nonterminal expressions
    record And(Expr left, Expr right) implements Expr {
        public boolean interpret(Map<String, String> ctx) { return left.interpret(ctx) && right.interpret(ctx); }
        @Override public String toString() { return "AND(" + left + ", " + right + ")"; }
    }

    record Or(Expr left, Expr right) implements Expr {
        public boolean interpret(Map<String, String> ctx) { return left.interpret(ctx) || right.interpret(ctx); }
        @Override public String toString() { return "OR(" + left + ", " + right + ")"; }
    }

    record Not(Expr inner) implements Expr {
        public boolean interpret(Map<String, String> ctx) { return !inner.interpret(ctx); }
        @Override public String toString() { return "NOT(" + inner + ")"; }
    }

    // Parser for:  expr := term ("or" term)*   term := factor ("and" factor)*
    //              factor := "not" factor | "(" expr ")" | IDENT OP (NUMBER | STRING)
    static final class Parser {
        private static final Pattern TOKEN = Pattern.compile("\\s*(==|!=|[<>()]|\"[^\"]*\"|[A-Za-z_][A-Za-z_0-9]*|\\d+)");
        private final List<String> tokens = new ArrayList<>();
        private int pos;

        Parser(String source) {
            Matcher m = TOKEN.matcher(source);
            int end = 0;
            while (m.lookingAt()) {
                tokens.add(m.group(1));
                end = m.end();
                m.region(end, source.length());
            }
            if (!source.substring(end).isBlank()) throw new IllegalArgumentException("bad input at: " + source.substring(end).strip());
        }

        static Expr parse(String source) {
            Parser p = new Parser(source);
            Expr e = p.expr();
            if (p.pos != p.tokens.size()) throw new IllegalArgumentException("unexpected " + p.tokens.get(p.pos));
            return e;
        }

        private Expr expr() {
            Expr e = term();
            while (accept("or")) e = new Or(e, term());
            return e;
        }

        private Expr term() {
            Expr e = factor();
            while (accept("and")) e = new And(e, factor());
            return e;
        }

        private Expr factor() {
            if (accept("not")) return new Not(factor());
            if (accept("(")) {
                Expr e = expr();
                expect(")");
                return e;
            }
            String field = next();
            String op = next();
            if (!List.of("==", "!=", "<", ">").contains(op)) throw new IllegalArgumentException("expected operator, got " + op);
            String literal = next();
            return new Compare(field, op, literal.replace("\"", ""));
        }

        private boolean accept(String t) {
            if (pos < tokens.size() && tokens.get(pos).equals(t)) {
                pos++;
                return true;
            }
            return false;
        }

        private void expect(String t) {
            if (!accept(t)) throw new IllegalArgumentException("expected " + t);
        }

        private String next() {
            if (pos >= tokens.size()) throw new IllegalArgumentException("unexpected end of rule");
            return tokens.get(pos++);
        }
    }

    public static void main(String[] args) {
        String rule = "port == 22 and not (src == \"10.0.0.5\" or user == \"root\") or port > 1023";
        Expr ast = Parser.parse(rule);
        System.out.println("rule: " + rule);
        System.out.println("ast:  " + ast);

        List<Map<String, String>> requests = List.of(
                Map.of("port", "22", "src", "10.0.0.9", "user", "deploy"),
                Map.of("port", "22", "src", "10.0.0.5", "user", "deploy"),
                Map.of("port", "22", "src", "10.0.0.9", "user", "root"),
                Map.of("port", "8080", "src", "10.0.0.5", "user", "root"),
                Map.of("port", "80", "src", "10.0.0.9", "user", "web"));
        for (Map<String, String> r : requests) {
            String desc = "port=" + r.get("port") + " src=" + r.get("src") + " user=" + r.get("user");
            System.out.printf("  %-38s -> %s%n", desc, ast.interpret(r) ? "match" : "no match");
        }

        try {
            Parser.parse("port == 22 and");
        } catch (IllegalArgumentException e) {
            System.out.println("invalid rule: " + e.getMessage());
        }
    }
}

/* Output:
rule: port == 22 and not (src == "10.0.0.5" or user == "root") or port > 1023
ast:  OR(AND(port==22, NOT(OR(src==10.0.0.5, user==root))), port>1023)
  port=22 src=10.0.0.9 user=deploy       -> match
  port=22 src=10.0.0.5 user=deploy       -> no match
  port=22 src=10.0.0.9 user=root         -> no match
  port=8080 src=10.0.0.5 user=root       -> match
  port=80 src=10.0.0.9 user=web          -> no match
invalid rule: unexpected end of rule
*/
