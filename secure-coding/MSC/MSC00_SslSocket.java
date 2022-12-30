// MSC00-J. Use SSLSocket rather than Socket for secure data exchange
// https://wiki.sei.cmu.edu/confluence/display/java/MSC00-J.+Use+SSLSocket+rather+than+Socket+for+secure+data+exchange
//
// A plain java.net.Socket sends bytes in the clear: anyone on the path can
// read and modify credentials or session data. For sensitive data use TLS
// through javax.net.ssl.SSLSocket (or a higher-level client such as
// HttpClient with https). Configure it properly: keep the JDK's default
// enabled protocols (TLSv1.3 and TLSv1.2; SSLv3, TLSv1 and TLSv1.1 are
// disabled), and enable hostname verification with
// setEndpointIdentificationAlgorithm("HTTPS"), which SSLSocket does NOT do
// by default. This program only creates unconnected sockets and inspects
// their configuration; it opens no network connection.
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class MSC00_SslSocket {
    public static void main(String[] args) throws Exception {
        // Noncompliant: a plain socket, no encryption and no peer authentication
        try (Socket plain = new Socket()) {
            System.out.println("noncompliant: " + plain.getClass().getSimpleName()
                    + ", is an SSLSocket: " + (plain instanceof SSLSocket));
        }

        // Compliant: an SSLSocket from the default factory
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        try (SSLSocket tls = (SSLSocket) factory.createSocket()) {
            List<String> enabled = Arrays.asList(tls.getEnabledProtocols());
            System.out.println("compliant: is an SSLSocket: " + (tls instanceof SSLSocket));
            System.out.println("  TLSv1.3 enabled: " + enabled.contains("TLSv1.3")
                    + ", TLSv1.2 enabled: " + enabled.contains("TLSv1.2"));
            System.out.println("  legacy enabled (SSLv3/TLSv1/TLSv1.1): "
                    + (enabled.contains("SSLv3") || enabled.contains("TLSv1") || enabled.contains("TLSv1.1")));

            // Hostname verification is off unless requested
            SSLParameters params = tls.getSSLParameters();
            System.out.println("  default endpoint identification: " + params.getEndpointIdentificationAlgorithm());
            params.setEndpointIdentificationAlgorithm("HTTPS");
            params.setProtocols(new String[] {"TLSv1.3", "TLSv1.2"});
            tls.setSSLParameters(params);
            System.out.println("  configured endpoint identification: "
                    + tls.getSSLParameters().getEndpointIdentificationAlgorithm()
                    + ", protocols " + Arrays.toString(tls.getEnabledProtocols()));
        }
    }
}

/* Output:
noncompliant: Socket, is an SSLSocket: false
compliant: is an SSLSocket: true
  TLSv1.3 enabled: true, TLSv1.2 enabled: true
  legacy enabled (SSLv3/TLSv1/TLSv1.1): false
  default endpoint identification: null
  configured endpoint identification: HTTPS, protocols [TLSv1.3, TLSv1.2]
*/
