// IDS14-J. Do not trust the contents of hidden form fields
// https://wiki.sei.cmu.edu/confluence/display/java/IDS14-J.+Do+not+trust+the+contents+of+hidden+form+fields
//
// An <input type="hidden"> is hidden from the page, not from the user. Its
// value travels in the request like any other parameter and can be changed
// with the browser's developer tools, a proxy or a hand-written request.
// Prices, user IDs, roles, "isAdmin" flags or the result of a check done
// in JavaScript must therefore never be read back from the client and
// trusted. Keep that state on the server (look it up by an identifier,
// store it in the session) or, if it must round-trip, protect it with a
// MAC and still validate it.
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class IDS14_HiddenFormFields {
    // The server's own catalogue: the only trustworthy source of prices
    static final Map<String, BigDecimal> CATALOGUE = Map.of(
            "book-17", new BigDecimal("39.90"),
            "pen-3", new BigDecimal("2.50"));

    // The page the server rendered:
    //   <input type="hidden" name="price" value="39.90">
    static final String HONEST_POST = "item=book-17&qty=2&price=39.90";
    // The same form, edited in the browser before submitting
    static final String TAMPERED_POST = "item=book-17&qty=2&price=0.01";

    public static void main(String[] args) {
        for (String body : new String[]{HONEST_POST, TAMPERED_POST}) {
            Map<String, String> form = parseForm(body);
            System.out.println("POST " + body);
            System.out.println("  noncompliant total: " + totalNoncompliant(form));
            System.out.println("  compliant total:    " + totalCompliant(form));
        }
        Map<String, String> unknown = parseForm("item=laptop-1&qty=1&price=1.00");
        try {
            totalCompliant(unknown);
        } catch (IllegalArgumentException e) {
            System.out.println("unknown item: " + e.getMessage());
        }
    }

    // Noncompliant: the price comes from the client
    static BigDecimal totalNoncompliant(Map<String, String> form) {
        BigDecimal price = new BigDecimal(form.get("price"));
        return price.multiply(new BigDecimal(form.get("qty")));
    }

    // Compliant: the client says WHAT it wants; the server decides what it costs
    static BigDecimal totalCompliant(Map<String, String> form) {
        BigDecimal price = CATALOGUE.get(form.get("item"));
        if (price == null) {
            throw new IllegalArgumentException("no such item");
        }
        int qty = Integer.parseInt(form.get("qty"));
        if (qty < 1 || qty > 100) {
            throw new IllegalArgumentException("bad quantity");
        }
        // form.get("price") is ignored entirely
        return price.multiply(BigDecimal.valueOf(qty));
    }

    static Map<String, String> parseForm(String body) {
        Map<String, String> form = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            form.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return form;
    }
}

/* Output:
POST item=book-17&qty=2&price=39.90
  noncompliant total: 79.80
  compliant total:    79.80
POST item=book-17&qty=2&price=0.01
  noncompliant total: 0.02
  compliant total:    79.80
unknown item: no such item
*/
