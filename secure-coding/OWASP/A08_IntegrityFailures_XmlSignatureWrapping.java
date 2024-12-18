// OWASP Top 10 2021: A08:2021 Software and Data Integrity Failures, XML signature wrapping (CWE-345)
// https://owasp.org/Top10/A08_2021-Software_and_Data_Integrity_Failures/
//
// In XML-DSig, as used by SAML, a signature does not cover "the document";
// it covers the element its Reference points to, usually by an ID
// attribute. Signature wrapping exploits the gap between two pieces of
// code: the verifier finds the referenced element and checks it, while
// the application separately searches for "the Assertion" (first match by
// tag name) and reads that. An attacker moves the genuinely signed element
// somewhere harmless and puts an unsigned one where the application looks.
// The fix is to use exactly the node the verifier validated (and to check
// that it sits where the protocol requires, with unique IDs). This is a
// toy model: an HMAC over the element's text stands in for XML-DSig, and
// no real signature library is involved.
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class A08_IntegrityFailures_XmlSignatureWrapping {
    static final byte[] IDP_KEY = "identity-provider-demo-key-32byt".getBytes(StandardCharsets.US_ASCII);

    public static void main(String[] args) throws Exception {
        String signedAlice = "<Assertion ID=\"a1\"><Subject>alice</Subject></Assertion>";
        String sig = hmacHex("alice");
        String genuine = "<Response>" + signedAlice
                + "<Signature Ref=\"a1\" Value=\"" + sig + "\"/></Response>";
        // Wrapped: attacker's unsigned Assertion first, the signed one hidden in an extension element
        String wrapped = "<Response><Assertion ID=\"evil\"><Subject>admin</Subject></Assertion>"
                + "<Extensions>" + signedAlice + "</Extensions>"
                + "<Signature Ref=\"a1\" Value=\"" + sig + "\"/></Response>";

        for (String[] c : new String[][]{{"genuine", genuine}, {"wrapped", wrapped}}) {
            Document doc = parse(c[1]);
            System.out.println(c[0] + " response:");
            System.out.println("  noncompliant: " + loginNoncompliant(doc));
            System.out.println("  compliant:    " + loginCompliant(doc));
        }
    }

    // Verifies the signature and returns the element it actually covers, or null
    static Element verifiedElement(Document doc) throws Exception {
        Element sig = (Element) doc.getElementsByTagName("Signature").item(0);
        if (sig == null) return null;
        String ref = sig.getAttribute("Ref");
        Element target = null;
        int withThatId = 0;
        NodeList all = doc.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            if (ref.equals(e.getAttribute("ID"))) {
                target = e;
                withThatId++;
            }
        }
        if (withThatId != 1) return null; // missing or duplicate IDs
        boolean ok = MessageDigest.isEqual(hmacHex(target.getTextContent()).getBytes(StandardCharsets.US_ASCII),
                sig.getAttribute("Value").getBytes(StandardCharsets.US_ASCII));
        return ok ? target : null;
    }

    // Noncompliant: verification and use look up the assertion independently
    static String loginNoncompliant(Document doc) throws Exception {
        if (verifiedElement(doc) == null) return "signature invalid";
        Element first = (Element) doc.getElementsByTagName("Assertion").item(0);
        return "signature valid, logged in as " + subject(first);
    }

    // Compliant: use the node that was verified, and require it in the expected place
    static String loginCompliant(Document doc) throws Exception {
        Element signed = verifiedElement(doc);
        if (signed == null) return "signature invalid";
        if (signed.getParentNode() != doc.getDocumentElement() || !signed.getTagName().equals("Assertion")) {
            return "rejected: signed assertion is not a direct child of Response";
        }
        if (doc.getElementsByTagName("Assertion").getLength() != 1) {
            return "rejected: more than one Assertion";
        }
        return "signature valid, logged in as " + subject(signed);
    }

    static String subject(Element assertion) {
        return assertion.getElementsByTagName("Subject").item(0).getTextContent();
    }

    static String hmacHex(String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(IDP_KEY, "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    }

    static Document parse(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); // no DTDs, no XXE
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}

/* Output:
genuine response:
  noncompliant: signature valid, logged in as alice
  compliant:    signature valid, logged in as alice
wrapped response:
  noncompliant: signature valid, logged in as admin
  compliant:    rejected: signed assertion is not a direct child of Response
*/
