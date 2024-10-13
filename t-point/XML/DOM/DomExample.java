// DOM (Document Object Model) parses a whole XML document into an in-memory
// tree of Node objects: Document -> Element -> Text/Attr/... You can walk it
// with getChildNodes()/getFirstChild(), search it with getElementsByTagName(),
// read attributes with getAttribute(), and modify it (createElement,
// appendChild, removeChild, setAttribute). It is the easiest API to use but
// keeps everything in memory, so it suits small to medium documents.
// Security note: a default DocumentBuilderFactory still processes DOCTYPEs,
// which opens the door to XXE and entity-expansion attacks. Always harden the
// factory before parsing untrusted input (see the secure-coding section for
// the full story); the minimal hardening is shown in newSecureFactory().
import java.io.StringReader;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

public class DomExample {
    static final String XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <library>
              <book id="b1" year="2018">
                <title>Effective Java</title>
                <author>Joshua Bloch</author>
              </book>
              <book id="b2" year="2006">
                <title>Java Concurrency in Practice</title>
                <author>Brian Goetz</author>
              </book>
            </library>
            """;

    static DocumentBuilderFactory newSecureFactory() throws ParserConfigurationException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        // No DOCTYPE at all: blocks XXE and "billion laughs" in one go
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        return dbf;
    }

    public static void main(String[] args) throws Exception {
        DocumentBuilder builder = newSecureFactory().newDocumentBuilder();
        builder.setErrorHandler(null); // don't let the default handler print to stderr
        Document doc = builder.parse(new InputSource(new StringReader(XML)));
        Element root = doc.getDocumentElement();
        System.out.println("root element: " + root.getTagName());

        // Search by tag name (in document order)
        NodeList books = doc.getElementsByTagName("book");
        System.out.println("books: " + books.getLength());
        for (int i = 0; i < books.getLength(); i++) {
            Element book = (Element) books.item(i);
            String title = book.getElementsByTagName("title").item(0).getTextContent();
            System.out.printf("  %s (%s) %s%n", book.getAttribute("id"), book.getAttribute("year"), title);
        }

        // Child nodes include the whitespace text between elements
        NodeList children = root.getChildNodes();
        int elements = 0, texts = 0;
        for (int i = 0; i < children.getLength(); i++) {
            switch (children.item(i).getNodeType()) {
                case Node.ELEMENT_NODE -> elements++;
                case Node.TEXT_NODE -> texts++;
                default -> { }
            }
        }
        System.out.println("children of <library>: " + elements + " elements, " + texts + " whitespace text nodes");

        // Modify the tree: add a book, change an attribute, remove a book
        Element added = doc.createElement("book");
        added.setAttribute("id", "b3");
        added.setAttribute("year", "2014");
        Element title = doc.createElement("title");
        title.setTextContent("Java 8 in Action");
        added.appendChild(title);
        root.appendChild(added);
        ((Element) books.item(0)).setAttribute("year", "2017");
        root.removeChild(books.item(1)); // NodeList is live: it shrinks immediately

        System.out.println("after edits: " + books.getLength() + " books");
        for (int i = 0; i < books.getLength(); i++) {
            Element book = (Element) books.item(i);
            System.out.println("  " + book.getAttribute("id") + " year=" + book.getAttribute("year"));
        }

        // getAttribute returns "" (not null) for a missing attribute
        System.out.println("missing attribute: \"" + added.getAttribute("isbn") + "\", hasAttribute: " + added.hasAttribute("isbn"));

        // The hardened factory rejects any DOCTYPE
        String withDoctype = "<!DOCTYPE x [<!ENTITY e \"boom\">]><x>&e;</x>";
        try {
            builder.parse(new InputSource(new StringReader(withDoctype)));
        } catch (SAXParseException e) {
            System.out.println("DOCTYPE rejected: " + e.getClass().getSimpleName());
        }
    }
}

/* Output:
root element: library
books: 2
  b1 (2018) Effective Java
  b2 (2006) Java Concurrency in Practice
children of <library>: 2 elements, 3 whitespace text nodes
after edits: 2 books
  b1 year=2017
  b3 year=2014
missing attribute: "", hasAttribute: false
DOCTYPE rejected: SAXParseException
*/
