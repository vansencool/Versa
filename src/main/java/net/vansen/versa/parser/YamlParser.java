package net.vansen.versa.parser;

import net.vansen.versa.comments.Comment;
import net.vansen.versa.comments.CommentType;
import net.vansen.versa.language.Language;
import net.vansen.versa.node.Node;
import net.vansen.versa.node.Value;
import net.vansen.versa.node.entry.Entry;
import net.vansen.versa.node.entry.EntryType;
import net.vansen.versa.node.value.ValueType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
@SuppressWarnings({"unused", "DataFlowIssue"})
public class YamlParser {

    private final String[] lines;
    public Consumer<String> errorHandler = System.out::println;
    private boolean strict = true;
    private boolean endsWithNewline;
    private int ln;

    public YamlParser(String s) {
        this.lines = split(s);
        this.endsWithNewline = s.endsWith("\n");
        ln = 0;
    }

    public YamlParser(String s, boolean strict) {
        this(s);
        this.strict = strict;
    }

    public void endWithNewLine(boolean endsWithNewline) {
        this.endsWithNewline = endsWithNewline;
    }

    public Node parse() {
        Node root = new Node();
        root.endsWithNewline(endsWithNewline);
        root.language(Language.YAML);

        Deque<Node> nodes = new ArrayDeque<>();
        Deque<Integer> indents = new ArrayDeque<>();

        nodes.push(root);
        indents.push(0);

        Node pending = null;
        int pendingIndent = -1;

        while (ln < lines.length) {
            String raw = lines[ln];
            int indent = countIndent(raw);
            String rest = raw.substring(indent);

            if (rest.isBlank()) {
                nodes.peek().order.add(new Entry(EntryType.EMPTY_LINE, null));
                ln++;
                continue;
            }

            if (rest.startsWith("#")) {
                Comment c = new Comment(CommentType.COMMENT_LINE, rest.substring(1), false);
                c.indent = indent;
                nodes.peek().order.add(new Entry(EntryType.COMMENT, c));
                ln++;
                continue;
            }

            int currentIndent = indents.peek();

            if (indent > currentIndent) {
                if (pending == null)
                    fail("Unexpected indentation (no parent to attach to)", raw);

                int diff = indent - currentIndent;

                if (root.indentUnit == -1)
                    root.indentUnit = diff;
                else if (diff != root.indentUnit)
                    fail("Invalid indentation increase (expected +" + root.indentUnit + ")", raw);

                nodes.push(pending);
                indents.push(indent);
                pending = null;
            } else {
                while (indent < indents.peek()) {
                    nodes.pop();
                    indents.pop();
                }

                if (indent != indents.peek())
                    fail("Indentation does not match any open level", raw);
            }

            Node current = nodes.peek();

            boolean isListItem = rest.startsWith("-");
            boolean isMappingKey = !isListItem && indexOfNonQuoted(rest) != -1;

            if (isListItem) {
                if (indent != indents.peek())
                    fail("List item indentation mismatch", raw);

                String rawVal = rest.substring(1);
                String clean = stripInlineComment(rawVal).trim();
                String inline = findInlineComment(rawVal);

                Value v = parseScalar(clean);
                if (inline != null)
                    v.comments.add(new Comment(CommentType.INLINE_VALUE, inline, false));

                current.order.add(new Entry(EntryType.VALUE, v));
                ln++;
                continue;
            }

            if (!isMappingKey)
                fail("Expected mapping key or list item", raw);

            int colon = indexOfNonQuoted(rest);
            if (colon == -1)
                fail("Expected ':'", raw);

            String key = rest.substring(0, colon).trim();
            String rawAfter = rest.substring(colon + 1);
            String cleanAfter = stripInlineComment(rawAfter).trim();
            String inline = findInlineComment(rawAfter);

            if (cleanAfter.isEmpty()) {
                Node n = new Node();
                n.name = key;
                n.language(Language.YAML);

                current.children.add(n);
                current.order.add(new Entry(EntryType.BRANCH, n));

                pending = n;

                ln++;
                continue;
            }

            Value v = parseScalar(cleanAfter);
            v.name = key;

            if (inline != null)
                v.comments.add(new Comment(CommentType.INLINE_VALUE, inline, false));

            current.values.put(key, v);
            current.order.add(new Entry(EntryType.VALUE, v));

            ln++;
        }

        if (pending != null)
            fail("Dangling mapping key without block", "");

        if (root.indentUnit <= 0)
            root.indentUnit = 2;

        return root;
    }

    private Value parseScalar(String s) {
        s = stripInlineComment(s).trim();
        Value v = new Value();

        if (s.startsWith("\"") && s.endsWith("\"")) {
            v.type = ValueType.STRING;
            v.sv = s.substring(1, s.length() - 1).replace("\\n", "\n");
            return v;
        }

        if (s.equals("true") || s.equals("false")) {
            v.type = ValueType.BOOL;
            v.iv = s.equals("true") ? 1 : 0;
            return v;
        }

        try {
            if (s.contains(".") || s.contains("e") || s.contains("E")) {
                v.type = ValueType.DOUBLE;
                v.dv = Double.parseDouble(s);
            } else {
                v.iv = Long.parseLong(s);
                v.type = (v.iv >= Integer.MIN_VALUE && v.iv <= Integer.MAX_VALUE)
                        ? ValueType.INT
                        : ValueType.LONG;
            }
            return v;
        } catch (Throwable ignore) {
        }

        v.type = ValueType.STRING;
        v.sv = s;
        return v;
    }

    private int countIndent(String s) {
        int c = 0;
        while (c < s.length() && s.charAt(c) == ' ') c++;
        return c;
    }

    private String stripInlineComment(String s) {
        boolean q = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && !(i > 0 && s.charAt(i - 1) == '\\')) q = !q;
            if (!q && c == '#') return s.substring(0, i);
        }
        return s;
    }

    private int indexOfNonQuoted(String s) {
        boolean q = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && !(i > 0 && s.charAt(i - 1) == '\\')) q = !q;
            if (!q && c == ':') return i;
        }
        return -1;
    }

    private static String[] split(String s) {
        int len = s.length(), count = 1;
        for (int i = 0; i < len; i++) if (s.charAt(i) == '\n') count++;
        String[] r = new String[count];
        int p = 0, start = 0;
        for (int i = 0; i < len; i++) {
            if (s.charAt(i) == '\n') {
                r[p++] = s.substring(start, i);
                start = i + 1;
            }
        }
        r[p] = start < len ? s.substring(start) : "";
        return r;
    }

    private String findInlineComment(String s) {
        boolean inQ = false;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c == '"' && !(i > 0 && s.charAt(i - 1) == '\\'))
                inQ = !inQ;

            if (!inQ && c == '#')
                return s.substring(i + 1);
        }

        return null;
    }

    private void fail(String msg, String line) {
        String m = "Line " + (ln + 1) + " -> " + msg + " | " + line;
        if (strict)
            throw new RuntimeException("YAML :: Parser -> " + m);
        errorHandler.accept(m);
    }
}
