// SAX (Simple API for XML) is a push parser: instead of building a tree it
// streams the document and calls back into a handler: startElement,
// characters, endElement, ... The handler keeps whatever state it needs, so
// memory use stays flat no matter how big the file is.
//  - characters() may be called several times for one text node (the parser
//    may split text at buffer or entity boundaries), so accumulate it in a
//    StringBuilder and use it in endElement()
//  - attributes arrive with startElement and are only valid during that call
//  - errors are reported through the ErrorHandler methods; throwing from the
//    handler aborts parsing
// Harden SAXParserFactory the same way as DOM before parsing untrusted input.
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.TreeMap;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

public class SaxExample {
    static final String XML = """
            <orders>
              <order id="1" customer="alice"><item sku="A1" qty="2" price="9.50"/><item sku="B7" qty="1" price="20.00"/></order>
              <order id="2" customer="bob"><item sku="A1" qty="5" price="9.50"/></order>
              <order id="3" customer="alice"><note>gift &amp; wrap</note><item sku="C3" qty="1" price="99.99"/></order>
            </orders>
            """;

    /** Totals per customer, computed while the document streams by. */
    static class OrderHandler extends DefaultHandler {
        final Map<String, Long> centsByCustomer = new TreeMap<>();
        final Deque<String> path = new ArrayDeque<>();
        final StringBuilder text = new StringBuilder();
        String customer;
        int events;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) {
            events++;
            path.push(qName);
            text.setLength(0);
            switch (qName) {
                case "order" -> customer = atts.getValue("customer");
                case "item" -> {
                    long cents = Math.round(Double.parseDouble(atts.getValue("price")) * 100);
                    long qty = Long.parseLong(atts.getValue("qty"));
                    centsByCustomer.merge(customer, cents * qty, Long::sum);
                }
                default -> { }
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            text.append(ch, start, length); // may arrive in pieces
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            events++;
            if (qName.equals("note")) {
                System.out.println("note at /" + String.join("/", path.reversed()) + ": " + text.toString().strip());
            }
            path.pop();
        }
    }

    public static void main(String[] args) throws Exception {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        SAXParser parser = spf.newSAXParser();

        OrderHandler handler = new OrderHandler();
        parser.parse(new InputSource(new StringReader(XML)), handler);
        System.out.println("element events: " + handler.events);
        handler.centsByCustomer.forEach((c, cents) ->
                System.out.printf("%-6s %d.%02d%n", c, cents / 100, cents % 100));

        // A handler can stop early by throwing; here: stop at the first order of "bob"
        DefaultHandler stopper = new DefaultHandler() {
            int seen;
            @Override
            public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
                if (qName.equals("order")) {
                    seen++;
                    if ("bob".equals(atts.getValue("customer"))) {
                        throw new SAXException("found bob after " + seen + " orders");
                    }
                }
            }
        };
        try {
            parser.parse(new InputSource(new StringReader(XML)), stopper);
        } catch (SAXException e) {
            System.out.println("stopped early: " + e.getMessage());
        }

        // Malformed input is reported as SAXParseException with a position
        try {
            parser.parse(new InputSource(new StringReader("<a><b></a>")), new DefaultHandler());
        } catch (SAXParseException e) {
            System.out.println("malformed: " + e.getClass().getSimpleName() + " at line " + e.getLineNumber());
        }
    }
}

/* Output:
note at /orders/order/note: gift & wrap
element events: 18
alice  138.99
bob    47.50
stopped early: found bob after 2 orders
malformed: SAXParseException at line 1
*/
