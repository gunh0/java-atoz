// Facade (structural): provide one simple, higher-level interface to a set of
// interfaces in a subsystem, making the subsystem easier to use.
//
// Onboarding a new employee touches several subsystems (directory, groups,
// MFA enrollment, laptop provisioning, audit log), each with its own API and
// its own required order. A facade offers onboard()/offboard() and hides the
// choreography; the subsystems stay available for callers that need more.
// A facade is also a good place for a security invariant: offboarding always
// revokes access BEFORE anything else, no matter who calls it.
// JDK examples: java.nio.file.Files (one call such as Files.readString hides
// providers, channels, decoders and buffers) and javax.crypto.Cipher, a
// front for the pluggable provider SPI classes.
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class FacadeDemo {
    static final List<String> AUDIT = new ArrayList<>();

    // --- subsystem classes, each with its own API ---
    static final class Directory {
        private final Set<String> active = new LinkedHashSet<>();
        String createAccount(String first, String last) {
            String id = (first.charAt(0) + last).toLowerCase();
            active.add(id);
            AUDIT.add("directory: created " + id);
            return id;
        }
        void disable(String id) { active.remove(id); AUDIT.add("directory: disabled " + id); }
        boolean isActive(String id) { return active.contains(id); }
    }

    static final class GroupService {
        void add(String id, String group) { AUDIT.add("groups: " + id + " -> " + group); }
        void removeAll(String id) { AUDIT.add("groups: removed " + id + " from all groups"); }
    }

    static final class MfaService {
        String enroll(String id) { AUDIT.add("mfa: enrollment link issued to " + id); return "ENROLL-" + id.toUpperCase(); }
        void revokeTokens(String id) { AUDIT.add("mfa: sessions and tokens revoked for " + id); }
    }

    static final class DeviceService {
        void assignLaptop(String id, String os) { AUDIT.add("devices: " + os + " laptop queued for " + id); }
        void wipe(String id) { AUDIT.add("devices: remote wipe scheduled for " + id); }
    }

    // --- the facade ---
    static final class OnboardingFacade {
        private final Directory directory = new Directory();
        private final GroupService groups = new GroupService();
        private final MfaService mfa = new MfaService();
        private final DeviceService devices = new DeviceService();

        String onboard(String first, String last, String team) {
            String id = directory.createAccount(first, last);
            groups.add(id, "all-staff");
            groups.add(id, team);
            mfa.enroll(id);
            devices.assignLaptop(id, team.equals("engineering") ? "linux" : "macos");
            return id;
        }

        void offboard(String id) {
            // access first: tokens, then the account, then the rest
            mfa.revokeTokens(id);
            directory.disable(id);
            groups.removeAll(id);
            devices.wipe(id);
        }

        boolean isActive(String id) { return directory.isActive(id); }
    }

    public static void main(String[] args) {
        OnboardingFacade hr = new OnboardingFacade();

        String id = hr.onboard("Jisoo", "Kim", "engineering");
        System.out.println("onboarded " + id + ", active=" + hr.isActive(id));
        hr.offboard(id);
        System.out.println("offboarded " + id + ", active=" + hr.isActive(id));

        System.out.println("audit trail:");
        AUDIT.forEach(line -> System.out.println("  " + line));
    }
}

/* Output:
onboarded jkim, active=true
offboarded jkim, active=false
audit trail:
  directory: created jkim
  groups: jkim -> all-staff
  groups: jkim -> engineering
  mfa: enrollment link issued to jkim
  devices: linux laptop queued for jkim
  mfa: sessions and tokens revoked for jkim
  directory: disabled jkim
  groups: removed jkim from all groups
  devices: remote wipe scheduled for jkim
*/
