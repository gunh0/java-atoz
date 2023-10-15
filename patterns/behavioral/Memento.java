// Memento (behavioral): without violating encapsulation, capture and
// externalize an object's internal state so that the object can be restored
// to this state later.
//
// Here a firewall policy editor (the originator) can save() a snapshot of its
// rules and restore() one later. The deployment tool (the caretaker) keeps a
// stack of snapshots for undo and a "last known good" checkpoint for
// rollback, but it cannot read or change them: Snapshot's state is private
// to the editor, the caretaker only sees a label. (In this single file all
// classes are nestmates, so javac would allow the access; in real code the
// editor and caretaker live in separate top-level classes.) Snapshots copy
// the rule list, so later edits never leak into an old snapshot.
// Compared with Command's undo, a memento restores the whole state at once,
// which is simpler when operations are hard to invert.
// JDK examples: serialization externalizes state in the same spirit, and
// javax.swing.undo keeps an UndoManager (caretaker) of UndoableEdits.
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class MementoDemo {
    // The originator
    static final class PolicyEditor {
        private final List<String> rules = new ArrayList<>();
        private String defaultAction = "deny";

        // The memento: an opaque token outside, full access inside PolicyEditor
        static final class Snapshot {
            private final List<String> rules;
            private final String defaultAction;
            private final String label;

            private Snapshot(List<String> rules, String defaultAction, String label) {
                this.rules = List.copyOf(rules); // defensive, immutable copy
                this.defaultAction = defaultAction;
                this.label = label;
            }

            String label() { return label; } // the only thing a caretaker may see
        }

        void addRule(String rule) { rules.add(rule); }
        void removeRule(String rule) { rules.remove(rule); }
        void setDefaultAction(String action) { defaultAction = action; }

        Snapshot save(String label) { return new Snapshot(rules, defaultAction, label); }

        void restore(Snapshot s) {
            rules.clear();
            rules.addAll(s.rules);
            defaultAction = s.defaultAction;
        }

        @Override
        public String toString() { return rules + " default=" + defaultAction; }
    }

    // The caretaker: stores snapshots, never looks inside
    static final class DeployTool {
        private final PolicyEditor editor;
        private final Deque<PolicyEditor.Snapshot> undo = new ArrayDeque<>();
        private PolicyEditor.Snapshot lastKnownGood;

        DeployTool(PolicyEditor editor) {
            this.editor = editor;
            lastKnownGood = editor.save("initial");
        }

        void edit(String description, Runnable change) {
            undo.push(editor.save("before " + description));
            change.run();
            System.out.println("edit  " + description + " -> " + editor);
        }

        void undo() {
            PolicyEditor.Snapshot s = undo.pop();
            editor.restore(s);
            System.out.println("undo  (" + s.label() + ") -> " + editor);
        }

        void deploy(boolean healthCheckPasses) {
            if (healthCheckPasses) {
                lastKnownGood = editor.save("deployed");
                undo.clear();
                System.out.println("deploy ok, checkpoint saved -> " + editor);
            } else {
                editor.restore(lastKnownGood);
                undo.clear();
                System.out.println("deploy FAILED, rolled back to '" + lastKnownGood.label() + "' -> " + editor);
            }
        }
    }

    public static void main(String[] args) {
        PolicyEditor editor = new PolicyEditor();
        DeployTool tool = new DeployTool(editor);

        tool.edit("allow https", () -> editor.addRule("allow tcp/443"));
        tool.edit("allow ssh", () -> editor.addRule("allow tcp/22"));
        tool.undo();
        tool.edit("allow dns", () -> editor.addRule("allow udp/53"));
        tool.deploy(true);

        tool.edit("drop https", () -> editor.removeRule("allow tcp/443"));
        tool.edit("default allow", () -> editor.setDefaultAction("allow"));
        tool.deploy(false); // e.g. the site went down: roll back everything since the checkpoint
    }
}

/* Output:
edit  allow https -> [allow tcp/443] default=deny
edit  allow ssh -> [allow tcp/443, allow tcp/22] default=deny
undo  (before allow ssh) -> [allow tcp/443] default=deny
edit  allow dns -> [allow tcp/443, allow udp/53] default=deny
deploy ok, checkpoint saved -> [allow tcp/443, allow udp/53] default=deny
edit  drop https -> [allow udp/53] default=deny
edit  default allow -> [allow udp/53] default=allow
deploy FAILED, rolled back to 'deployed' -> [allow tcp/443, allow udp/53] default=deny
*/
