package net.vansen.versa.parser;

import net.vansen.versa.comments.Comment;
import net.vansen.versa.comments.CommentType;
import net.vansen.versa.logger.VersaLog;
import net.vansen.versa.node.Node;
import net.vansen.versa.node.Value;
import net.vansen.versa.node.entry.Entry;
import net.vansen.versa.node.entry.EntryType;
import net.vansen.versa.node.value.ValueType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * Core text parser for the Versa configuration format.
 * Converts raw config text into a {@link Node} tree while preserving structure,
 * comments, lists, branches, and formatting order.
 * <p>
 * The parser is line-based. Multiple statements on the same line are not supported,
 * and doing so will cause the first key to be parsed normally while everything after
 * it becomes part of the value string. Each value/branch entry must begin on its own line.
 * <p>
 * When {@link #strict} is enabled, invalid syntax throws an exception.
 * When disabled, errors are logged using {@link #errorHandler} and parsing continues.
 */

@SuppressWarnings({"unused", "DataFlowIssue"})
public class VersaParser {
    private final String[] lines;
    private final Deque<Node> stack = new ArrayDeque<>();
    public Consumer<String> errorHandler = System.out::println;
    private boolean strict = true;
    private int ln;

    /**
     * Creates a new Versa parser with strict mode ON by default.
     *
     * @param s configuration text
     */
    public VersaParser(@NotNull String s) {
        this.lines = split(s);
        ln = 0;
    }

    /**
     * Creates a Versa parser with optional strict behavior.
     *
     * @param s      configuration text
     * @param strict whether to throw errors instead of logging them
     */
    public VersaParser(@NotNull String s, boolean strict) {
        this(s);
        this.strict = strict;
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

    /**
     * Parses the input and returns the root {@link Node}.
     *
     * @return parsed configuration tree
     */
    public @NotNull Node parse() {
        Node root = new Node();
        stack.push(root);

        while (ln < lines.length) {
            String raw = lines[ln];
            int col = 0;
            while (col < raw.length() && (raw.charAt(col) == ' ' || raw.charAt(col) == '\t')) col++;
            String rest = raw.substring(col).strip();

            if (rest.isBlank()) {
                stack.peek().order.add(new Entry(EntryType.EMPTY_LINE, null));
                ln++;
                continue;
            }

            if (rest.startsWith("//") || rest.startsWith("#")) {
                boolean slash = rest.startsWith("//");
                String text = slash ? rest.substring(2) : rest.substring(1);
                stack.peek().order.add(new Entry(
                        EntryType.COMMENT,
                        new Comment(CommentType.COMMENT_LINE, text, slash)
                ));
                ln++;
                continue;
            }

            int braceIdx = indexOfNonQuoted(rest, '{');
            int eqIdx = indexOfNonQuoted(rest, '=');
            int colonIdx = indexOfNonQuoted(rest, ':');

            int assignIdx = -1;
            char assign = '=';

            if (eqIdx != -1 && colonIdx != -1) {
                assignIdx = Math.min(eqIdx, colonIdx);
                assign = (eqIdx < colonIdx) ? '=' : ':';
            } else if (eqIdx != -1) {
                assignIdx = eqIdx;
                assign = '=';
            } else if (colonIdx != -1) {
                assignIdx = colonIdx;
                assign = ':';
            }

            if (braceIdx != -1 && (assignIdx == -1 || braceIdx < assignIdx)) {
                String name = rest.substring(0, braceIdx).trim();
                if (name.isEmpty()) fail("Missing branch name before '{'. Example: section {", rest);

                Node n = new Node();
                n.name = name;
                stack.peek().children.add(n);
                stack.peek().order.add(new Entry(EntryType.BRANCH, n));
                stack.push(n);

                addInlineComment(rest, braceIdx + 1, n, CommentType.START_BRANCH);
                ln++;
                continue;
            }

            if (rest.startsWith("}")) {
                if (stack.size() == 1) fail("Unexpected '}' — no branch is open to close", rest);
                Node popped = stack.pop();
                addInlineComment(rest, 1, popped, CommentType.END_BRANCH);
                ln++;
                continue;
            }

            if (assignIdx != -1) {
                String key = rest.substring(0, assignIdx).trim();
                String after = rest.substring(assignIdx + 1).trim();

                if (key.isEmpty()) fail("Missing key before assignment", rest);
                if (after.isEmpty()) fail("Missing value after assignment. Example: " + key + " = 10", rest);

                Value v = parseValueFromLines(after);
                v.name = key;
                v.assign = assign;

                addInlineComment(rest, assignIdx + 1, v, CommentType.INLINE_VALUE);

                stack.peek().values.put(key, v);
                stack.peek().order.add(new Entry(EntryType.VALUE, v));
                ln++;
                continue;
            }

            error(rest);
            ln++;
        }

        if (stack.size() > 1)
            error("Reached end of file but '" + stack.peek().name + "' was never closed with '}'");

        return root;
    }

    private void fail(String msg, String line) {
        String m = "Line " + (ln + 1) + " -> " + msg + " | " + line;
        if (strict) throw new VersaParseException("VERSA :: Parser         -> " + m);
        VersaLog.error("Parser", m);
    }

    private void error(String msg) {
        String m = "Line " + (ln + 1) + " -> " + msg;
        if (strict) throw new VersaParseException("VERSA :: Parser         -> " + m);
        VersaLog.warn("Parser", m);
    }

    private Value parseValueFromLines(String start) {
        StringBuilder acc = new StringBuilder(start);
        if (start.isEmpty()) {
            ln++;
            if (ln < lines.length) acc.append("\n").append(lines[ln]);
        }

        int br = 0, sq = 0;
        boolean inQ = false;
        int l = ln;

        while (true) {
            String line = (l == ln) ? acc.toString() : lines[l];
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);

                if (c == '"' && !(i > 0 && line.charAt(i - 1) == '\\'))
                    inQ = !inQ;

                if (!inQ) {
                    if (c == '{') br++;
                    else if (c == '}') br--;
                    else if (c == '[') sq++;
                    else if (c == ']') sq--;
                }
            }

            if (!inQ && br <= 0 && sq <= 0) break;

            l++;
            if (l >= lines.length) {
                fail("Value never closed -> missing ']' or '}' or closing quote", acc.toString());
                break;
            }

            acc.append("\n").append(lines[l]);
        }

        if (l > ln) ln = l;
        String token = acc.toString().trim();
        return parseValueFromString(token);
    }

    private Value parseValueFromString(String token) {
        token = stripInlineComment(token).trim();
        Value v = new Value();

        if (token.startsWith("\"")) {
            if (!token.endsWith("\"")) fail("Missing closing quote", token);
            v.type = ValueType.STRING;
            v.sv = token.substring(1, token.length() - 1).replace("\\n", "\n");
            return v;
        }

        if (token.startsWith("[")) {
            if (!token.endsWith("]")) fail("List missing ']'", token);

            String inner = token.substring(1, token.length() - 1).trim();
            if (inner.isEmpty()) {
                v.type = ValueType.LIST;
                v.list = new ArrayList<>();
                return v;
            }

            List<Value> parts = parseList(inner);

            boolean b = false, val = false;
            for (Value x : parts) {
                if (x.branchList != null) b = true;
                else val = true;
            }

            if (b && val) fail("Mixed list types", inner);

            if (b) {
                v.type = ValueType.LIST_OF_BRANCHES;
                v.branchList = new ArrayList<>();
                for (Value x : parts) v.branchList.addAll(x.branchList);
                return v;
            }

            v.type = ValueType.LIST;
            v.list = new ArrayList<>();
            v.list.addAll(parts);
            return v;
        }

        if (token.equals("true") || token.equals("false")) {
            v.type = ValueType.BOOL;
            v.iv = token.equals("true") ? 1 : 0;
            return v;
        }

        try {
            if (token.contains(".") || token.contains("e") || token.contains("E")) {
                v.type = ValueType.DOUBLE;
                v.dv = Double.parseDouble(token);
            } else {
                v.iv = Long.parseLong(token);
                v.type = (v.iv >= Integer.MIN_VALUE && v.iv <= Integer.MAX_VALUE) ? ValueType.INT : ValueType.LONG;
            }
            return v;
        } catch (Throwable ignore) {
        }

        if (token.startsWith("{") && token.endsWith("}")) {
            Node n = new VersaParser(token).parse();
            Value out = new Value();
            out.type = ValueType.LIST_OF_BRANCHES;
            out.branchList = n.children;
            return out;
        }

        v.type = ValueType.STRING;
        v.sv = stripQuotes(token).replace("\\n", "\n");
        return v;
    }

    private Value parseListElement(String s) {
        s = s.trim();
        if (s.startsWith("{") && s.endsWith("}")) {
            String wrap = "root " + s;
            Node n = new VersaParser(wrap).parse();
            Value v = new Value();
            v.type = ValueType.LIST_OF_BRANCHES;
            v.branchList = Collections.singletonList(n.children.get(0));
            return v;
        }
        return parseValueFromString(s);
    }

    private List<Value> parseList(String s) {
        List<Value> out = new ArrayList<>();
        int d = 0;
        boolean q = false;
        int start = 0;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && !(i > 0 && s.charAt(i - 1) == '\\')) q = !q;
            if (!q) {
                if (c == '{' || c == '[') d++;
                if (c == '}' || c == ']') d--;
            }
            if (c == ',' && d == 0) {
                out.add(parseListElement(s.substring(start, i).trim()));
                start = i + 1;
            }
        }

        if (start < s.length()) out.add(parseListElement(s.substring(start).trim()));
        return out;
    }

    private String stripQuotes(String s) {
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) return s.substring(1, s.length() - 1);
        return s;
    }

    private String stripInlineComment(String s) {
        boolean inQ = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && !(i > 0 && s.charAt(i - 1) == '\\')) inQ = !inQ;
            if (!inQ) {
                if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') return s.substring(0, i);
                if (c == '#') return s.substring(0, i);
            }
        }
        return s;
    }

    private String findInlineComment(String line, int start) {
        int i = start;
        boolean inQ = false;
        for (; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && !(i > 0 && line.charAt(i - 1) == '\\')) inQ = !inQ;
            if (!inQ) {
                if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') return line.substring(i + 2);
                if (c == '#') return line.substring(i + 1);
            }
        }
        return null;
    }

    private void addInlineComment(String line, int start, Object target, CommentType type) {
        boolean inQ = false;
        int len = line.length();

        for (int i = start; i < len; i++) {
            char c = line.charAt(i);
            if (c == '"' && !(i > 0 && line.charAt(i - 1) == '\\')) inQ = !inQ;

            if (!inQ) {
                boolean slash = (c == '/' && i + 1 < len && line.charAt(i + 1) == '/');
                boolean hash = (c == '#');

                if (slash || hash) {
                    String raw = line.substring(i + (slash ? 2 : 1));
                    Comment comment = new Comment(type, raw, slash);

                    if (target instanceof Node n) n.inlineComments.add(comment);
                    else if (target instanceof Value v) v.comments.add(comment);

                    return;
                }
            }
        }
    }

    private int indexOfNonQuoted(String s, char ch) {
        boolean inQ = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && !(i > 0 && s.charAt(i - 1) == '\\')) inQ = !inQ;
            if (!inQ && c == ch) return i;
        }
        return -1;
    }

    public static class VersaParseException extends RuntimeException {
        public VersaParseException(String s) {
            super(s);
        }
    }
}