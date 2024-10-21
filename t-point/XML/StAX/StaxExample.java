// StAX (Streaming API for XML, javax.xml.stream) is a pull parser: your code
// asks for the next event (next() returns START_ELEMENT, CHARACTERS,
// END_ELEMENT, ...) instead of receiving callbacks. That keeps SAX's low
// memory use while letting ordinary loops and methods follow the document
// structure, and it is easy to stop early.
// XMLStreamWriter is the matching writer: it escapes text and attribute
// values for you, so no hand-built "<" + tag + ">" strings.
// Security: for untrusted input turn off DTD support and external entities
// on the XMLInputFactory (SUPPORT_DTD, IS_SUPPORTING_EXTERNAL_ENTITIES).
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

public class StaxExample {
    record Event(String user, String action, String target) { }

    static final String XML = """
            <audit>
              <event user="alice" action="login"/>
              <event user="bob" action="read"><target>/etc/app.conf</target></event>
              <event user="mallory" action="delete"><target>/var/log/app.log</target></event>
            </audit>
            """;

    static XMLInputFactory secureInputFactory() {
        XMLInputFactory xif = XMLInputFactory.newInstance();
        xif.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        xif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return xif;
    }

    static List<Event> read(String xml) throws XMLStreamException {
        List<Event> events = new ArrayList<>();
        XMLStreamReader r = secureInputFactory().createXMLStreamReader(new StringReader(xml));
        try {
            String user = null, action = null, target = null;
            while (r.hasNext()) {
                int type = r.next();
                if (type == XMLStreamConstants.START_ELEMENT) {
                    switch (r.getLocalName()) {
                        case "event" -> {
                            user = r.getAttributeValue(null, "user");
                            action = r.getAttributeValue(null, "action");
                            target = null;
                        }
                        // getElementText() reads up to the matching end tag
                        case "target" -> target = r.getElementText();
                        default -> { }
                    }
                } else if (type == XMLStreamConstants.END_ELEMENT && r.getLocalName().equals("event")) {
                    events.add(new Event(user, action, target));
                }
            }
        } finally {
            r.close();
        }
        return events;
    }

    static String write(List<Event> events) throws XMLStreamException {
        StringWriter out = new StringWriter();
        XMLStreamWriter w = XMLOutputFactory.newInstance().createXMLStreamWriter(out);
        w.writeStartElement("report");
        for (Event e : events) {
            w.writeStartElement("entry");
            w.writeAttribute("who", e.user());
            w.writeCharacters(e.action() + (e.target() == null ? "" : " " + e.target()));
            w.writeEndElement();
        }
        w.writeEndElement();
        w.close();
        return out.toString();
    }

    public static void main(String[] args) throws Exception {
        List<Event> events = read(XML);
        events.forEach(System.out::println);

        // The writer escapes markup characters in text and attributes
        List<Event> tricky = List.of(new Event("eve\" onload=\"x", "post", "<script>&</script>"));
        System.out.println(write(events.subList(0, 2)));
        System.out.println(write(tricky));

        // Round trip: what we wrote is well-formed and reads back the same
        XMLStreamReader r = secureInputFactory().createXMLStreamReader(new StringReader(write(tricky)));
        r.nextTag();          // <report>
        r.nextTag();          // <entry>
        System.out.println("who  = " + r.getAttributeValue(null, "who"));
        System.out.println("text = " + r.getElementText());

        // With SUPPORT_DTD=false, a document that relies on an entity fails
        try {
            read("<!DOCTYPE a [<!ENTITY x \"boom\">]><a>&x;</a>");
        } catch (XMLStreamException e) {
            System.out.println("entity with DTD disabled: " + e.getClass().getSimpleName());
        }
    }
}

/* Output:
Event[user=alice, action=login, target=null]
Event[user=bob, action=read, target=/etc/app.conf]
Event[user=mallory, action=delete, target=/var/log/app.log]
<report><entry who="alice">login</entry><entry who="bob">read /etc/app.conf</entry></report>
<report><entry who="eve&quot; onload=&quot;x">post &lt;script&gt;&amp;&lt;/script&gt;</entry></report>
who  = eve" onload="x
text = post <script>&</script>
entity with DTD disabled: XMLStreamException
*/
