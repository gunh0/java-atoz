// IDS16-J. Prevent XML Injection
// https://wiki.sei.cmu.edu/confluence/display/java/IDS16-J.+Prevent+XML+Injection
//
// Building an XML document by string concatenation lets input that
// contains markup change the document's STRUCTURE. Below, an order is sent
// to a back end as XML; the price is fixed by the server and only the
// quantity comes from the user. A "quantity" that closes the element and
// adds its own <price> overrides the real price, because the receiver takes
// the first <price> it finds (other parsers take the last one; either way the
// attacker decides). Fixes: validate the input strictly (a quantity
// is a small positive integer), and build XML with an API that escapes text
// content (DOM + Transformer, StAX), never with string templates.
import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

public class IDS16_XmlInjection {
    static final String PRICE = "500.00";

    public static void main(String[] args) throws Exception {
        String[] quantities = {"2", "1</quantity><price>0.01</price><quantity>1"};
        for (String q : quantities) {
            System.out.println("quantity input: " + q);
            String bad = orderNoncompliant(q);
            System.out.println("  noncompliant xml:   " + bad);
            System.out.println("  back end charges:   " + firstPrice(bad));
            System.out.println("  compliant validate: " + orderValidated(q));
            String escaped = orderWithDom(q);
            System.out.println("  compliant DOM xml:  " + escaped);
            System.out.println("  back end charges:   " + firstPrice(escaped));
        }
    }

    // Noncompliant: string concatenation
    static String orderNoncompliant(String quantity) {
        return "<item><description>Widget</description><quantity>" + quantity + "</quantity>"
                + "<price>" + PRICE + "</price></item>";
    }

    // Compliant 1: strict validation
    static String orderValidated(String quantity) {
        if (!quantity.matches("[1-9][0-9]{0,2}")) {
            return "rejected";
        }
        return orderNoncompliant(quantity); // safe now: only digits can reach the template
    }

    // Compliant 2: an XML API escapes text content
    static String orderWithDom(String quantity) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element item = doc.createElement("item");
        doc.appendChild(item);
        for (String[] field : new String[][] {{"description", "Widget"}, {"quantity", quantity}, {"price", PRICE}}) {
            Element e = doc.createElement(field[0]);
            e.setTextContent(field[1]);
            item.appendChild(e);
        }
        var transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter out = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(out));
        return out.toString();
    }

    // The receiving side: parses the XML and uses the first <price>
    static String firstPrice(String xml) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));
        return doc.getElementsByTagName("price").item(0).getTextContent()
                + " x " + doc.getElementsByTagName("quantity").item(0).getTextContent();
    }
}

/* Output:
quantity input: 2
  noncompliant xml:   <item><description>Widget</description><quantity>2</quantity><price>500.00</price></item>
  back end charges:   500.00 x 2
  compliant validate: <item><description>Widget</description><quantity>2</quantity><price>500.00</price></item>
  compliant DOM xml:  <item><description>Widget</description><quantity>2</quantity><price>500.00</price></item>
  back end charges:   500.00 x 2
quantity input: 1</quantity><price>0.01</price><quantity>1
  noncompliant xml:   <item><description>Widget</description><quantity>1</quantity><price>0.01</price><quantity>1</quantity><price>500.00</price></item>
  back end charges:   0.01 x 1
  compliant validate: rejected
  compliant DOM xml:  <item><description>Widget</description><quantity>1&lt;/quantity&gt;&lt;price&gt;0.01&lt;/price&gt;&lt;quantity&gt;1</quantity><price>500.00</price></item>
  back end charges:   500.00 x 1</quantity><price>0.01</price><quantity>1
*/
