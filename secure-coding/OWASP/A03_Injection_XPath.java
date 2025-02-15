// OWASP Top 10 2021: A03:2021 Injection, XPath injection (CWE-643)
// https://owasp.org/Top10/A03_2021-Injection/
//
// XPath has no prepared statements in the string sense, so login checks
// against an XML user store are often built by concatenation:
//   //user[name='<input>' and password='<input>']
// A quote in the input ends the literal and the rest becomes XPath:
// ' or '1'='1 logs in without a password, and because XPath has no access
// control inside the document, a login form that only answers yes/no is
// enough to read any value character by character (blind injection with
// starts-with()). The fix is the XPath equivalent of a bind variable: write
// $name and $password in the expression and supply the values through an
// XPathVariableResolver; they are then compared as strings and never parsed.
// (A real user store would of course hold password hashes, see A02.)
import java.io.StringReader;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class A03_Injection_XPath {
    static final String USERS = """
            <users>
              <user><name>alice</name><password>s3cr3t</password><role>admin</role></user>
              <user><name>bob</name><password>hunter2</password><role>user</role></user>
            </users>""";

    static Document doc;

    public static void main(String[] args) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        doc = f.newDocumentBuilder().parse(new InputSource(new StringReader(USERS)));

        String[][] attempts = {
            {"bob", "hunter2"},
            {"bob", "wrong"},
            {"alice' or '1'='1", "x"},
            {"bob", "' or '1'='1"},
        };
        for (String[] a : attempts) {
            System.out.printf("login(%s, %s)%n  noncompliant: %s%n  compliant:    %s%n",
                    a[0], a[1], loginNoncompliant(a[0], a[1]), loginCompliant(a[0], a[1]));
        }

        // Blind extraction: the yes/no answer of the login form is an oracle
        System.out.println("blind extraction of alice's password through the login form");
        System.out.println("  noncompliant recovered: " + extract(true));
        System.out.println("  compliant recovered:    " + extract(false));
    }

    // Noncompliant: input concatenated into the expression
    static String loginNoncompliant(String name, String password) throws XPathExpressionException {
        String expr = "//user[name='" + name + "' and password='" + password + "']";
        return result((NodeList) XPathFactory.newInstance().newXPath()
                .evaluate(expr, doc, XPathConstants.NODESET));
    }

    // Compliant: a fixed expression with variables resolved from a map
    static String loginCompliant(String name, String password) throws XPathExpressionException {
        Map<String, String> vars = Map.of("name", name, "password", password);
        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setXPathVariableResolver((QName q) -> vars.get(q.getLocalPart()));
        return result((NodeList) xpath.evaluate("//user[name=$name and password=$password]",
                doc, XPathConstants.NODESET));
    }

    static String result(NodeList users) {
        if (users.getLength() == 0) {
            return "denied";
        }
        Element first = (Element) users.item(0);
        return "logged in as " + first.getElementsByTagName("name").item(0).getTextContent()
                + " (" + first.getElementsByTagName("role").item(0).getTextContent() + ")";
    }

    // Guess one character at a time; "logged in" means the guessed prefix is right
    static String extract(boolean vulnerable) throws XPathExpressionException {
        String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder known = new StringBuilder();
        outer:
        while (known.length() < 32) {
            for (char c : alphabet.toCharArray()) {
                String probe = "' or (name='alice' and starts-with(password,'" + known + c + "')) or 'a'='b";
                String answer = vulnerable ? loginNoncompliant(probe, "x") : loginCompliant(probe, "x");
                if (!answer.equals("denied")) {
                    known.append(c);
                    continue outer;
                }
            }
            break;
        }
        return known.isEmpty() ? "(nothing)" : known.toString();
    }
}

/* Output:
login(bob, hunter2)
  noncompliant: logged in as bob (user)
  compliant:    logged in as bob (user)
login(bob, wrong)
  noncompliant: denied
  compliant:    denied
login(alice' or '1'='1, x)
  noncompliant: logged in as alice (admin)
  compliant:    denied
login(bob, ' or '1'='1)
  noncompliant: logged in as alice (admin)
  compliant:    denied
blind extraction of alice's password through the login form
  noncompliant recovered: s3cr3t
  compliant recovered:    (nothing)
*/
