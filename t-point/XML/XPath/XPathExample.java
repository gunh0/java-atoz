// XPath is a small query language for XML trees: /a/b selects children,
// //b selects descendants anywhere, [@attr='v'] and [n] are predicates, and
// functions like count(), sum(), contains() and string() compute values.
// In Java, XPathFactory -> XPath -> compile(expr) -> evaluate(doc, type) runs
// a query over a DOM; the XPathConstants type decides what you get back
// (NODESET, NODE, STRING, NUMBER, BOOLEAN), and evaluateExpression() (Java 9)
// returns a typed result directly.
// Never build an expression by concatenating user input (that's XPath
// injection, the XML cousin of SQL injection). Use $variables bound through
// an XPathVariableResolver instead, as in findUser() below.
import java.io.StringReader;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class XPathExample {
    static final String XML = """
            <users>
              <user name="alice" role="admin"><dept>security</dept><logins>42</logins></user>
              <user name="bob" role="dev"><dept>platform</dept><logins>17</logins></user>
              <user name="carol" role="dev"><dept>security</dept><logins>8</logins></user>
            </users>
            """;

    public static void main(String[] args) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(XML)));
        XPath xp = XPathFactory.newInstance().newXPath();

        // NODESET: all users in the security department
        NodeList sec = (NodeList) xp.evaluate("/users/user[dept='security']", doc, XPathConstants.NODESET);
        System.out.print("security:");
        for (int i = 0; i < sec.getLength(); i++) {
            System.out.print(" " + ((Element) sec.item(i)).getAttribute("name"));
        }
        System.out.println();

        // STRING, NUMBER, BOOLEAN results
        System.out.println("first user:   " + xp.evaluate("/users/user[1]/@name", doc));
        System.out.println("last dept:    " + xp.evaluate("//user[last()]/dept", doc));
        System.out.println("count(dev):   " + xp.evaluateExpression("count(//user[@role='dev'])", doc, Integer.class));
        System.out.println("sum(logins):  " + xp.evaluateExpression("sum(//logins)", doc, Double.class));
        System.out.println("any > 40:     " + xp.evaluateExpression("//user[logins > 40]", doc, Boolean.class));
        System.out.println("names with o: " + xp.evaluateExpression("count(//user[contains(@name,'o')])", doc, Integer.class));

        // Compile once, evaluate many times (also relative to a context node)
        XPathExpression loginsOf = xp.compile("number(logins)");
        for (int i = 0; i < sec.getLength(); i++) {
            System.out.println("  logins of security user " + (i + 1) + ": "
                    + ((Double) loginsOf.evaluate(sec.item(i), XPathConstants.NUMBER)).intValue());
        }

        // Injection: the classic login-bypass input
        String evil = "nobody' or '1'='1";
        String concatenated = "count(/users/user[@name='" + evil + "'])";
        System.out.println("concatenated query matches: " + xp.evaluate(concatenated, doc));
        System.out.println("parameterized query matches: " + findUser(doc, evil));
        System.out.println("parameterized 'bob' matches: " + findUser(doc, "bob"));
    }

    /** Binds the user name as $name, so it is always treated as a value. */
    static int findUser(Document doc, String name) throws Exception {
        XPath xp = XPathFactory.newInstance().newXPath();
        Map<QName, Object> vars = Map.of(new QName("name"), name);
        xp.setXPathVariableResolver(vars::get);
        return xp.evaluateExpression("count(/users/user[@name=$name])", doc, Integer.class);
    }
}

/* Output:
security: alice carol
first user:   alice
last dept:    security
count(dev):   2
sum(logins):  67.0
any > 40:     true
names with o: 2
  logins of security user 1: 42
  logins of security user 2: 8
concatenated query matches: 3
parameterized query matches: 0
parameterized 'bob' matches: 1
*/
