// Trie (prefix tree): each edge is a character, each node marks whether a word
// ends there. insert/contains/startsWith cost O(length of the key), independent
// of how many words are stored, and all words sharing a prefix live under one
// node, which makes autocomplete a subtree walk. Children are kept in a TreeMap
// so the walk yields words in sorted order. delete unmarks the word and prunes
// nodes that no longer lead to any word.
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public class Trie {
    private static final class Node {
        final Map<Character, Node> children = new TreeMap<>();
        boolean word;
    }

    private final Node root = new Node();
    private int size;

    public boolean insert(String key) {
        Node node = root;
        for (char c : key.toCharArray()) {
            node = node.children.computeIfAbsent(c, k -> new Node());
        }
        if (node.word) {
            return false;
        }
        node.word = true;
        size++;
        return true;
    }

    public boolean contains(String key) {
        Node node = find(key);
        return node != null && node.word;
    }

    public boolean startsWith(String prefix) {
        return find(prefix) != null;
    }

    public List<String> withPrefix(String prefix) {
        List<String> out = new ArrayList<>();
        Node node = find(prefix);
        if (node != null) {
            collect(node, new StringBuilder(prefix), out);
        }
        return out;
    }

    public boolean delete(String key) {
        boolean[] removed = {false};
        delete(root, key, 0, removed);
        if (removed[0]) {
            size--;
        }
        return removed[0];
    }

    // Returns true when the child at this depth can be pruned
    private boolean delete(Node node, String key, int depth, boolean[] removed) {
        if (depth == key.length()) {
            if (!node.word) {
                return false;
            }
            node.word = false;
            removed[0] = true;
            return node.children.isEmpty();
        }
        Node child = node.children.get(key.charAt(depth));
        if (child == null) {
            return false;
        }
        if (delete(child, key, depth + 1, removed)) {
            node.children.remove(key.charAt(depth));
        }
        return !node.word && node.children.isEmpty();
    }

    public int size() {
        return size;
    }

    public int nodeCount() {
        return count(root);
    }

    private int count(Node node) {
        int n = 1;
        for (Node child : node.children.values()) {
            n += count(child);
        }
        return n;
    }

    private Node find(String s) {
        Node node = root;
        for (int i = 0; i < s.length() && node != null; i++) {
            node = node.children.get(s.charAt(i));
        }
        return node;
    }

    private void collect(Node node, StringBuilder path, List<String> out) {
        if (node.word) {
            out.add(path.toString());
        }
        for (Map.Entry<Character, Node> e : node.children.entrySet()) {
            path.append(e.getKey());
            collect(e.getValue(), path, out);
            path.setLength(path.length() - 1);
        }
    }

    public static void main(String[] args) {
        Trie trie = new Trie();
        String[] words = {"java", "javac", "javadoc", "jar", "jvm", "jshell", "jdk", "jdeps", "javap"};
        for (String w : words) {
            trie.insert(w);
        }
        System.out.println("size=" + trie.size() + " nodes=" + trie.nodeCount());
        System.out.println("insert duplicate 'jar': " + trie.insert("jar"));
        System.out.println("contains java: " + trie.contains("java") + ", jav: " + trie.contains("jav"));
        System.out.println("startsWith jav: " + trie.startsWith("jav") + ", jx: " + trie.startsWith("jx"));
        System.out.println("prefix 'java': " + trie.withPrefix("java"));
        System.out.println("prefix 'jd':   " + trie.withPrefix("jd"));
        System.out.println("prefix '':     " + trie.withPrefix(""));

        System.out.println("delete javadoc: " + trie.delete("javadoc") + " (nodes now " + trie.nodeCount() + ")");
        System.out.println("delete java:    " + trie.delete("java") + " (javac still there: " + trie.contains("javac") + ")");
        System.out.println("delete jav:     " + trie.delete("jav"));
        System.out.println("prefix 'java' after deletes: " + trie.withPrefix("java"));

        // Self-check: prefix queries agree with a TreeSet's subSet view
        TreeSet<String> reference = new TreeSet<>(List.of(words));
        reference.remove("javadoc");
        reference.remove("java");
        boolean ok = true;
        for (String p : List.of("", "j", "ja", "jav", "javac", "jd", "js", "x")) {
            List<String> expected = new ArrayList<>(reference.subSet(p, p + Character.MAX_VALUE));
            ok &= expected.equals(trie.withPrefix(p));
        }
        System.out.println("prefix queries match TreeSet.subSet: " + ok);
    }
}

/* Output:
size=9 nodes=23
insert duplicate 'jar': false
contains java: true, jav: false
startsWith jav: true, jx: false
prefix 'java': [java, javac, javadoc, javap]
prefix 'jd':   [jdeps, jdk]
prefix '':     [jar, java, javac, javadoc, javap, jdeps, jdk, jshell, jvm]
delete javadoc: true (nodes now 20)
delete java:    true (javac still there: true)
delete jav:     false
prefix 'java' after deletes: [javac, javap]
prefix queries match TreeSet.subSet: true
*/
