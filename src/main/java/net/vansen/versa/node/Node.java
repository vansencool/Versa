package net.vansen.versa.node;

import net.vansen.versa.comments.Comment;
import net.vansen.versa.comments.CommentType;
import net.vansen.versa.language.Language;
import net.vansen.versa.node.entry.Entry;
import net.vansen.versa.node.entry.EntryType;
import net.vansen.versa.node.insert.InsertPoint;
import net.vansen.versa.node.value.ValueType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <h2>Node - A configuration section/block with values, child nodes and layout awareness</h2>
 *
 * <p>
 * A {@code Node} is one part of a hierarchical configuration tree in Versa configs.
 * It stores:
 * </p>
 *
 * <ul>
 *     <li><b>key/value pairs</b> — <code>host = "localhost"</code></li>
 *     <li><b>child nodes</b> — nested sections/tables</li>
 *     <li><b>inline comments + line comments</b></li>
 *     <li><b>empty lines for spacing</b></li>
 *     <li><b>print order</b> so layout is preserved on save</li>
 * </ul>
 *
 * <p>
 * Most config formats only parse → data → reformat everything,
 * losing comments and structure in the process.
 * <b>Node keeps the layout intact</b> so configs can be edited programmatically
 * without destroying human readability.
 * </p>
 *
 * <hr>
 * <h3>Quick Example | building + printing a config</h3>
 *
 * <pre><code>
 * Node root = new Node();
 * root.name = "database";
 *
 * root.addLineComment(" Connection settings")
 *     .setValue("host", "localhost")
 *     .setValue("port", 3306)
 *     .setValueComment("host", " Recommended: 127.0.0.1 or localhost")
 *     .emptyLine();
 *
 * Node pool = new Node();
 * pool.name = "pool";
 * pool.setValue("size", 10);
 *
 * root.addBranch(pool);
 *
 * root.before("port").comment(" Port of the database");
 * root.after("host").emptyLine();
 *
 * root.beforeBranch("pool").comment(" Database pool settings");
 *
 * root.save(Path.of("config.versa"));
 * </code></pre>
 *
 * <p><b>Generated output:</b></p>
 *
 * <pre><code>
 * // Connection settings
 * host = "localhost" // Recommended: 127.0.0.1 or localhost
 *
 * // Port of the database
 * port = 3306
 *
 * // Database pool settings
 * pool {
 *     size = 10
 * }
 * </code></pre>
 *
 * <hr>
 * <h3>Parsing from text using Versa</h3>
 * <p>
 * The {@code Versa} class is the entry-point for parsing config strings/files.
 * It reads Versa configuration format and produces a {@code Node} tree that preserves
 * layout as closely as possible (comments, blank lines, order, etc).
 * </p>
 *
 * <pre><code>
 * Node root = Versa.parse("config.versa"); // Parse to Node tree
 *
 * // Modify it
 * root.setValue("debug", true);
 *
 * // Write back (format remains similar to original)
 * root.save(Path.of("config.versa"));
 * </code></pre>
 *
 * <p>
 * Round-trip similarity is usually ~95–99%. The small difference comes mostly from
 * indentation normalization, since Versa intentionally reformats indentation rather
 * than preserving the user's original spacing style.
 * </p>
 *
 *
 * <hr>
 * <h3>Inline comments</h3>
 *
 * <pre><code>
 * root.setValue("max_players", 100);
 * root.setValueComment("max_players", " Soft limit");
 * </code></pre>
 *
 * <pre><code>
 * max_players = 100 // Soft limit
 * </code></pre>
 *
 *
 * <hr>
 * <h3>Ordering utilities</h3>
 * <p>These let you insert things relative to existing nodes/values.</p>
 *
 * <ul>
 *   <li><code>before("key")</code> → insert before value</li>
 *   <li><code>after("key")</code> → insert after value</li>
 *   <li><code>beforeBranch("name")</code> → insert before child node</li>
 *   <li><code>afterBranch("name")</code> → insert after child node</li>
 * </ul>
 *
 * <hr>
 * <h3>Tips</h3>
 * <ul>
 *     <li>Leading space after <code>//</code> makes comments nicer</li>
 *     <li><code>.emptyLine()</code> helps keep configs readable</li>
 *     <li>Root is always unnamed</li>
 * </ul>
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class Node {
    /**
     * Name of this branch. May be empty.
     */
    public String name = "";

    /**
     * Map of values in this branch, keyed by name.
     */
    public Map<String, Value> values = new LinkedHashMap<>();

    /**
     * Child branches directly under this node.
     */
    public List<Node> children = new ArrayList<>();

    /**
     * Stores comments that are attached to this node rather than being standalone lines.
     * <p>
     * Examples:
     * <pre>
     * key = 10 // inline value comment
     *
     * section { // start-branch comment
     *     ...
     * } // end-branch comment
     * </pre>
     * <p>
     * Notes:<br>
     * • These comments are printed only when the node or value itself is printed.<br>
     * • Standalone comments belong in {@link #order} as ENTRY.COMMENT instead.<br>
     * • This list does not insert new lines, it attaches to existing elements.
     */
    public List<Comment> inlineComments = new ArrayList<>();

    /**
     * Ordered view of this node's contents for printing.
     */
    public List<Entry> order = new ArrayList<>();

    /**
     * This value is detected once while parsing by observing the
     * first increase in indentation depth and is then propagated to
     * child nodes during serialization.
     * <p>
     * Notes:<br>
     * • This field is supported by both Versa and YAML formats.<br>
     * • A value {@code <= 0} means the indent unit is unknown and a
     *   default will be used.<br>
     * • Standalone comments store their absolute indentation separately
     *   and do not rely on this value.
     */
    public int indentUnit = -1;

    /**
     * Whether the original configuration text ended with a newline character, used in serialization.
     */
    private boolean endsWithNewline;

    /**
     * The preferred language to use when serializing this node via
     * language-agnostic methods such as {@link #toString()}.
     * <p>
     * This field does not affect how the node stores data internally.
     * All nodes remain fully mutable and editable at runtime, regardless
     * of language.
     * <p>
     * Parsing may auto-detect the input language, but this value determines
     * the default output language unless explicitly overridden.
     */
    private Language language = Language.VERSA;

    /**
     * Returns the first child branch with the given name.
     *
     * @return The first child branch, or {@code null} if not found
     */
    public Node getBranch(@NotNull String n) {
        for (Node c : children) if (c.name.equals(n)) return c;
        return null;
    }

    /**
     * Searches this node <b>and all of its child nodes</b> recursively for a value
     * with the given key.
     * <pre><code>
     * version = "1.0"
     * database {
     *     host = "localhost"
     *     pool {
     *         size = 10
     *     }
     * }
     *
     * getValueDeep("host")    → "localhost"
     * getValueDeep("size")    → 10
     * getValueDeep("version") → "1.0"
     * getValueDeep("missing") → null
     * </code></pre>
     *
     * @param key name of the value to search for
     * @return the first matching {@code Value}, or {@code null} if not found anywhere
     */
    public Value getValueFromAnywhere(@NotNull String key) {
        Value v = values.get(key);
        if (v != null) return v;
        for (Node c : children) {
            v = c.getValueFromAnywhere(key);
            if (v != null) return v;
        }
        return null;
    }

    /**
     * Resolves a value using a <b>dot-based path</b>, walking through child nodes.
     * Similar to <code>database.pool.size</code> lookup in config files.
     * <p>
     * Path rules:
     *
     * <ul>
     *     <li><b>"value"</b> → looks in this node only</li>
     *     <li><b>"child.value"</b> → search subtree one level deep</li>
     *     <li><b>"a.b.c.value"</b> → walk nodes until final key</li>
     * </ul>
     *
     * <pre><code>
     * database {
     *     host = "localhost"
     *     pool {
     *         size = 10
     *     }
     * }
     *
     * getValuePath("host")            → "localhost"
     * getValuePath("pool.size")       → 10
     * getValuePath("database.pool.size") → works if called on root
     * getValuePath("foo.bar")         → null (branch doesn't exist)
     * getValuePath("size")            → null unless on 'pool' node
     * </code></pre>
     *
     * @param path a dot-separated lookup like <code>"branch.sub.value"</code>
     * @return the {@code Value} if path resolves, otherwise {@code null}
     */
    public Value getValue(@NotNull String path) {
        String[] parts = path.split("\\.");
        Node n = this;
        for (int i = 0; i < parts.length; i++) {
            if (i == parts.length - 1) return n.values.get(parts[i]);
            Node next = null;
            for (Node c : n.children)
                if (c.name.equals(parts[i])) {
                    next = c;
                    break;
                }
            if (next == null) return null;
            n = next;
        }
        return null;
    }

    /**
     * Checks whether a dotted lookup path resolves to an actual value
     * inside this node or nested child nodes.
     *
     * @param path lookup path
     * @return true if a value exists at that path
     */
    public boolean hasPath(@NotNull String path) {
        return getValue(path) != null;
    }

    /**
     * Checks if any value with the given key exists anywhere in this node,
     * including nested children (searches recursively).
     *
     * @param key raw value name such as "port"
     * @return true if found anywhere in hierarchy
     */
    public boolean hasKey(@NotNull String key) {
        return getValueFromAnywhere(key) != null;
    }

    /**
     * Resolves a path and returns a String or null if missing.
     *
     * @param path lookup path
     * @return string or null
     */
    public @Nullable String getString(@NotNull String path) {
        Value v = getValue(path);
        return v == null ? null : v.asString();
    }

    /**
     * Resolves a path into an Integer or null.
     *
     * @param path path lookup
     * @return integer or null
     */
    public @Nullable Integer getInteger(@NotNull String path) {
        Value v = getValue(path);
        return v == null ? null : v.asInt();
    }

    /**
     * Resolves a path into a Long or null.
     *
     * @param path path lookup
     * @return long or null
     */
    public @Nullable Long getLong(@NotNull String path) {
        Value v = getValue(path);
        return v == null ? null : v.asLong();
    }

    /**
     * Resolves a path into a Double or null.
     *
     * @param path path lookup
     * @return double or null
     */
    public @Nullable Double getDouble(@NotNull String path) {
        Value v = getValue(path);
        return v == null ? null : v.asDouble();
    }

    /**
     * Resolves a path into a Boolean or null.
     *
     * @param path path lookup
     * @return boolean or null
     */
    public @Nullable Boolean getBool(@NotNull String path) {
        Value v = getValue(path);
        return v == null ? null : v.asBool();
    }

    /**
     * Gets a list value from a path, if present.
     *
     * @param path path lookup
     * @return raw list or null
     */
    public @Nullable List<Value> getList(@NotNull String path) {
        Value v = getValue(path);
        return v == null ? null : v.asList();
    }

    /**
     * Gets a list-of-branches value from a path.
     *
     * @param path path lookup
     * @return list of nodes or null
     */
    public @Nullable List<Node> getBranchList(@NotNull String path) {
        Value v = getValue(path);
        return v == null ? null : v.asBranchList();
    }

    /**
     * Converts list values into a {@code List<String>}.
     *
     * @param path path lookup
     * @return list of strings or null
     */
    public @Nullable List<String> getStringList(@NotNull String path) {
        List<Value> l = getList(path);
        if (l == null) return null;
        List<String> out = new ArrayList<>();
        for (Value v : l) out.add(v.asString());
        return out;
    }

    /**
     * Converts list values into {@code List<Integer>}.
     *
     * @param path path lookup
     * @return integer list or null
     */
    public @Nullable List<Integer> getIntegerList(@NotNull String path) {
        List<Value> l = getList(path);
        if (l == null) return null;
        List<Integer> out = new ArrayList<>();
        for (Value v : l) out.add(v.asInt());
        return out;
    }

    /**
     * Returns a string at the given path or a fallback if missing.
     *
     * @param path lookup path
     * @param def  returned if no value exists
     * @return string value or {@code def}
     */
    public String getString(@NotNull String path, @Nullable String def) {
        Value v = getValue(path);
        return v == null ? def : v.asString();
    }

    /**
     * Returns an integer at the given path or default.
     *
     * @param path lookup path
     * @param def  fallback if missing
     * @return integer value or {@code def}
     */
    public int getInteger(@NotNull String path, int def) {
        Value v = getValue(path);
        return v == null ? def : v.asInt();
    }

    /**
     * Returns a long at path or fallback.
     *
     * @param path lookup path
     * @param def  returned if not present
     * @return resolved long value or {@code def}
     */
    public long getLong(@NotNull String path, long def) {
        Value v = getValue(path);
        return v == null ? def : v.asLong();
    }

    /**
     * Returns a double at path or fallback.
     *
     * @param path lookup path
     * @param def  default value if missing
     * @return double value or {@code def}
     */
    public double getDouble(@NotNull String path, double def) {
        Value v = getValue(path);
        return v == null ? def : v.asDouble();
    }

    /**
     * Returns a boolean or fallback.
     *
     * @param path lookup path
     * @param def  default if missing
     * @return boolean or {@code def}
     */
    public boolean getBool(@NotNull String path, boolean def) {
        Value v = getValue(path);
        return v == null ? def : v.asBool();
    }

    /**
     * Returns a raw list type or fallback.
     *
     * @param path lookup path
     * @param def  used if not found
     * @return {@code List<Value>} or default
     */
    public @NotNull List<Value> getList(@NotNull String path, @NotNull List<Value> def) {
        Value v = getValue(path);
        return v == null ? def : v.asList();
    }

    /**
     * Returns a branch list or fallback.
     *
     * @param path lookup path
     * @param def  returned if missing
     * @return list of {@link Node} or default
     */
    public @NotNull List<Node> getBranchList(@NotNull String path, @NotNull List<Node> def) {
        Value v = getValue(path);
        return v == null ? def : v.asBranchList();
    }

    /**
     * Converts a list to list of strings.
     *
     * @param path lookup path
     * @param def  used if missing
     * @return list of strings or fallback
     */
    public @NotNull List<String> getStringList(@NotNull String path, @NotNull List<String> def) {
        List<Value> l = getList(path);
        if (l == null) return def;
        List<String> out = new ArrayList<>();
        for (Value v : l) out.add(v.asString());
        return out;
    }

    /**
     * Converts a list to integers.
     *
     * @param path lookup path
     * @param def  returned if missing
     * @return list of integers or fallback
     */
    public @NotNull List<Integer> getIntegerList(@NotNull String path, @NotNull List<Integer> def) {
        List<Value> l = getList(path);
        if (l == null) return def;
        List<Integer> out = new ArrayList<>();
        for (Value v : l) out.add(v.asInt());
        return out;
    }

    /**
     * Replaces existing comment type for this node and applies new text.
     *
     * @param t   comment classification
     * @param txt content or {@code null} for empty
     * @return same node for chaining
     */
    public @NotNull Node setComment(@NotNull CommentType t, @Nullable String txt) {
        inlineComments.removeIf(c -> c.type == t);
        inlineComments.add(new Comment(t, txt));
        return this;
    }

    /**
     * Creates or updates a value entry with Java-friendly types.
     * Supported: Boolean, Integer, Long, Float, Double, String.
     *
     * @param name key name
     * @param v    value object
     * @return node for chaining
     */
    public @NotNull Node setValue(@NotNull String name, @Nullable Object v) {
        Value val = new Value();
        val.name = name;
        if (v instanceof Boolean b) {
            val.type = ValueType.BOOL;
            val.iv = b ? 1 : 0;
        } else if (v instanceof Integer i) {
            val.type = ValueType.INT;
            val.iv = i;
        } else if (v instanceof Long l) {
            val.type = ValueType.LONG;
            val.iv = l;
        } else if (v instanceof Float f) {
            val.type = ValueType.FLOAT;
            val.dv = f;
        } else if (v instanceof Double d) {
            val.type = ValueType.DOUBLE;
            val.dv = d;
        } else if (v instanceof String s) {
            val.type = ValueType.STRING;
            val.sv = s;
        }
        values.put(name, val);
        order.add(new Entry(EntryType.VALUE, val));
        return this;
    }

    /**
     * Applies or replaces inline comment for a specific key.
     *
     * @param key key whose comment should change
     * @param txt new inline text
     * @return same node
     */
    public @NotNull Node setValueComment(@NotNull String key, @Nullable String txt) {
        Value v = values.get(key);
        if (v != null) {
            v.comments.removeIf(c -> c.type == CommentType.INLINE_VALUE);
            v.comments.add(new Comment(CommentType.INLINE_VALUE, txt));
        }
        return this;
    }

    /**
     * Appends a standalone printed line comment.
     *
     * @param text text placed after `//`
     * @return node for chaining
     */
    public @NotNull Node addLineComment(@Nullable String text) {
        order.add(new Entry(EntryType.COMMENT, new Comment(CommentType.COMMENT_LINE, text)));
        return this;
    }

    /**
     * Inserts an empty line visually in the config.
     *
     * @return node for chaining
     */
    public @NotNull Node emptyLine() {
        order.add(new Entry(EntryType.EMPTY_LINE, ""));
        return this;
    }

    /**
     * Adds an inline comment after this node's opening '{'.
     * Always preserved during formatting and printing.
     *
     * @param text  the comment text (printed as-is, no automatic spacing)
     * @param slash true to print using "//", false to print using "#"
     * @return this node for chaining
     */
    public Node addStartComment(@NotNull String text, boolean slash) {
        inlineComments.add(new Comment(CommentType.START_BRANCH, text, slash));
        return this;
    }

    /**
     * Adds an inline comment after this node's closing '}'.
     *
     * @param text  the comment text
     * @param slash true = //comment, false = #comment
     * @return this node for chaining
     */
    public Node addEndComment(@NotNull String text, boolean slash) {
        inlineComments.add(new Comment(CommentType.END_BRANCH, text, slash));
        return this;
    }

    /**
     * Same as {@link #addStartComment(String, boolean)} but uses '//' by default.
     *
     * @param text comment text
     * @return this node for chaining
     */
    public Node addStartComment(@NotNull String text) {
        return addStartComment(text, true);
    }

    /**
     * Same as {@link #addEndComment(String, boolean)} but uses '//' by default.
     *
     * @param text comment text
     * @return this node for chaining
     */
    public Node addEndComment(@NotNull String text) {
        return addEndComment(text, true);
    }

    /**
     * Adds a START_BRANCH inline comment to a direct child branch.
     * If the named branch does not exist, nothing happens.
     *
     * @param branch name of child branch
     * @param text   comment text
     * @param slash  true = //comment, false = #comment
     * @return this node for chaining
     */
    public Node addStartCommentTo(@NotNull String branch, @NotNull String text, boolean slash) {
        for (Node n : children) {
            if (n.name.equals(branch)) {
                n.inlineComments.add(new Comment(CommentType.START_BRANCH, text, slash));
                return this;
            }
        }
        return this;
    }

    /**
     * Adds an END_BRANCH inline comment to a direct child branch.
     * Does nothing if branch doesn't exist.
     *
     * @param branch name of branch
     * @param text   comment text
     * @param slash  true = //, false = #
     * @return this node for chaining
     */
    public Node addEndCommentTo(@NotNull String branch, @NotNull String text, boolean slash) {
        for (Node n : children) {
            if (n.name.equals(branch)) {
                n.inlineComments.add(new Comment(CommentType.END_BRANCH, text, slash));
                return this;
            }
        }
        return this;
    }

    /**
     * Sets the preferred output language for this node.
     * <p>
     * This affects the behavior of {@link #toString()} only and does not
     * modify the underlying data or formatting metadata.
     *
     * @param language the language to use when serializing this node
     * @return this node (for chaining)
     */
    public Node language(Language language) {
        this.language = language;
        return this;
    }

    /**
     * Returns the preferred language used when serializing this node
     * via language-agnostic methods such as {@link #toString()}.
     *
     * @return the current output language
     */
    public Language language() {
        return language;
    }

    /**
     * Returns whether this configuration should end with a newline
     * when serialized.
     *
     * @return {@code true} if a trailing newline should be emitted
     */
    public boolean endsWithNewline() {
        return endsWithNewline;
    }

    /**
     * Sets whether this configuration should end with a newline
     * when serialized.
     * <p>
     * This method allows callers to explicitly control end-of-file
     * formatting regardless of the original input.
     *
     * @param endsWithNewline {@code true} to emit a trailing newline
     * @return this node (for chaining)
     */
    public Node endsWithNewline(boolean endsWithNewline) {
        this.endsWithNewline = endsWithNewline;
        return this;
    }

    /**
     * Adds a child branch to this node and preserves ordering.
     *
     * @param child branch to append
     * @return this node
     */
    public Node addBranch(@NotNull Node child) {
        children.add(child);
        order.add(new Entry(EntryType.BRANCH, child));
        return this;
    }

    /**
     * Creates an insertion point before a value with matching key.
     * If not found, insertion defaults to end of the node.
     *
     * @param key target value key
     * @return insertion point used for comment placement, value insertion, etc.
     */
    public @NotNull InsertPoint before(@NotNull String key) {
        for (int i = 0; i < order.size(); i++) {
            Entry e = order.get(i);
            if (e.t == EntryType.VALUE && ((Value) e.o).name.equals(key))
                return new InsertPoint(this, i);
        }
        return new InsertPoint(this, order.size());
    }

    /**
     * Creates an insertion point after a value with matching key.
     * If not found, insertion defaults to end.
     *
     * @param key value name
     * @return insertion point
     */
    public @NotNull InsertPoint after(@NotNull String key) {
        for (int i = 0; i < order.size(); i++) {
            Entry e = order.get(i);
            if (e.t == EntryType.VALUE && ((Value) e.o).name.equals(key))
                return new InsertPoint(this, i + 1);
        }
        return new InsertPoint(this, order.size());
    }

    /**
     * Creates an insertion point before a branch with given name.
     * If none exists, inserts at end.
     *
     * @param name branch name
     * @return insertion point
     */
    public @NotNull InsertPoint beforeBranch(@NotNull String name) {
        for (int i = 0; i < order.size(); i++) {
            Entry e = order.get(i);
            if (e.t == EntryType.BRANCH && ((Node) e.o).name.equals(name))
                return new InsertPoint(this, i);
        }
        return new InsertPoint(this, order.size());
    }

    /**
     * Creates an insertion point after a branch with given name.
     *
     * @param name branch to search
     * @return new insertion point, or end if not found
     */
    public @NotNull InsertPoint afterBranch(@NotNull String name) {
        for (int i = 0; i < order.size(); i++) {
            Entry e = order.get(i);
            if (e.t == EntryType.BRANCH && ((Node) e.o).name.equals(name))
                return new InsertPoint(this, i + 1);
        }
        return new InsertPoint(this, order.size());
    }

    /**
     * Serializes this node and its children using the Versa configuration syntax.
     * <p>
     * The {@code depth} parameter controls the logical nesting level and is
     * translated into indentation using {@link #indentUnit} when available,
     * otherwise a default is used.
     * <p>
     * Notes:<br>
     * • This method always emits Versa syntax, regardless of {@link #language}.<br>
     * • Both Versa and YAML respect {@link #indentUnit} for structural indentation.<br>
     * • Formatting, ordering, and comments are preserved as stored in the node.<br>
     * • Inline comments are rendered only when attached to the element they
     *   belong to.<br>
     * • End-of-file newline behavior is controlled by {@link #endsWithNewline}.
     *
     * @param depth the current nesting depth
     * @return the Versa representation of this node
     */
    public String toStringVersa(int depth) {
        int unit = indentUnit > 0 ? indentUnit : 4;
        String pad = " ".repeat(depth * unit);
        StringBuilder sb = new StringBuilder();

        for (Entry e : order) {

            if (e.t == EntryType.EMPTY_LINE) {
                sb.append("\n");
                continue;
            }

            if (e.t == EntryType.COMMENT) {
                Comment c = (Comment) e.o;
                String prefix = c.slash ? "//" : "#";
                String[] lines = c.text.split("\\R");

                for (String line : lines) {
                    sb.append(pad)
                            .append(prefix)
                            .append(line)
                            .append("\n");
                }
                continue;
            }

            if (e.t == EntryType.VALUE) {
                Value v = (Value) e.o;

                if (v.name == null)
                    continue;

                sb.append(pad)
                        .append(v.name)
                        .append(v.assign == ':' ? ": " : " = ")
                        .append(v.toStringVersa());

                for (Comment c : v.comments)
                    if (c.type == CommentType.INLINE_VALUE) {
                        if (c.text.indexOf('\n') != -1)
                            throw new IllegalStateException("Inline comment cannot be multiline");

                        sb.append(" ").append(c.slash ? "//" : "#").append(c.text);
                    }

                sb.append("\n");
                continue;
            }

            if (e.t == EntryType.BRANCH) {
                Node ch = (Node) e.o;
                ch.indentUnit = unit;

                boolean hasUnnamed = false;
                boolean hasNamed = false;

                for (Entry ce : ch.order) {
                    if (ce.t == EntryType.VALUE) {
                        Value v = (Value) ce.o;
                        if (v.name == null) hasUnnamed = true;
                        else hasNamed = true;
                    }
                }

                if (hasUnnamed && !hasNamed) {
                    sb.append(pad)
                            .append(ch.name)
                            .append(" = [\n");

                    for (Entry ce : ch.order) {
                        if (ce.t != EntryType.VALUE) continue;
                        Value v = (Value) ce.o;
                        if (v.name != null) continue;

                        sb.append(" ".repeat((depth + 1) * unit))
                                .append(v.toStringVersa())
                                .append(",\n");
                    }

                    int len = sb.length();
                    if (sb.charAt(len - 2) == ',') {
                        sb.setLength(len - 2);
                        sb.append("\n");
                    }

                    sb.append(pad).append("]\n");

                    for (Entry ce : ch.order) {
                        if (ce.t == EntryType.COMMENT || ce.t == EntryType.EMPTY_LINE)
                            sb.append(pad).append(ce.t == EntryType.COMMENT
                                    ? ((Comment) ce.o).slash ? "//" + ((Comment) ce.o).text : "#" + ((Comment) ce.o).text
                                    : "").append("\n");
                    }

                    continue;
                }

                sb.append(pad).append(ch.name).append(" {");

                for (Comment c : ch.inlineComments)
                    if (c.type == CommentType.START_BRANCH) {
                        String[] lines = c.text.split("\\R");
                        sb.append(" ").append(c.slash ? "//" : "#").append(lines[0]).append("\n");

                        for (int i = 1; i < lines.length; i++)
                            sb.append(" ".repeat((depth + 1) * unit))
                                    .append(c.slash ? "//" : "#")
                                    .append(lines[i])
                                    .append("\n");
                    }

                sb.append("\n");
                sb.append(ch.toStringVersa(depth + 1));
                sb.append(pad).append("}");

                for (Comment c : ch.inlineComments)
                    if (c.type == CommentType.END_BRANCH) {
                        String[] lines = c.text.split("\\R");
                        sb.append(" ").append(c.slash ? "//" : "#").append(lines[0]).append("\n");

                        for (int i = 1; i < lines.length; i++)
                            sb.append(" ".repeat((depth + 1) * unit))
                                    .append(c.slash ? "//" : "#")
                                    .append(lines[i])
                                    .append("\n");
                    }

                sb.append("\n");
            }
        }

        if (depth == 0) {
            int len = sb.length();
            while (len > 0 && sb.charAt(len - 1) == '\n')
                len--;

            sb.setLength(len);

            if (endsWithNewline)
                sb.append('\n');
        }

        return sb.toString();
    }

    /**
     * Serializes this node as a top-level Versa configuration document.
     *
     * @return the Versa configuration text
     */
    public String toStringVersa() {
        return toStringVersa(0);
    }

    /**
     * Serializes this node using the language specified by {@link #language}.
     *
     * @return the rendered configuration text
     */
    @Override
    public String toString() {
        if (language == Language.VERSA) {
            return toStringVersa();
        }
        else if (language == Language.YAML) {
            return toStringYaml();
        }
        throw new RuntimeException("Please add the new language to Node#toString!");
    }

    /**
     * Serializes this node and its children into YAML format.
     * <p>
     * The {@code depth} parameter represents the logical nesting level
     * and is converted to spaces using {@link #indentUnit}.
     * <p>
     * Notes:<br>
     * • This method is YAML-specific and is not used by the Versa format.<br>
     * • Structural indentation is computed as {@code depth * indentUnit}.<br>
     * • Standalone comments use their own absolute indentation and do not
     *   rely on {@code depth}.<br>
     *
     * @param depth the current YAML indentation depth
     * @return the YAML representation of this node
     */
    public String toStringYaml(int depth) {
        int unit = indentUnit > 0 ? indentUnit : 2;
        String pad = " ".repeat(depth * unit);

        StringBuilder sb = new StringBuilder();

        for (Entry e : order) {

            if (e.t == EntryType.EMPTY_LINE) {
                sb.append("\n");
                continue;
            }

            if (e.t == EntryType.COMMENT) {
                Comment c = (Comment) e.o;
                String[] lines = c.text.split("\\R");
                for (String line : lines) {
                    sb.append(" ".repeat(c.indent))
                            .append("#")
                            .append(line)
                            .append("\n");
                }
                continue;
            }

            if (e.t == EntryType.VALUE) {
                Value v = (Value) e.o;

                if (v.name == null) {
                    sb.append(pad)
                            .append("- ")
                            .append(v.toStringYaml())
                            .append("\n");
                    continue;
                }

                String rendered = v.toStringYaml();

                sb.append(pad).append(v.name).append(":");

                if (rendered.indexOf('\n') != -1) {
                    sb.append("\n");
                    for (String line : rendered.split("\\R"))
                        sb.append(pad)
                                .append(" ".repeat(unit))
                                .append(line)
                                .append("\n");
                } else {
                    sb.append(" ").append(rendered).append("\n");
                }

                for (Comment c : v.comments)
                    if (c.type == CommentType.INLINE_VALUE)
                        sb.append(" #").append(c.text);

                sb.append("\n");
                continue;
            }

            if (e.t == EntryType.BRANCH) {
                Node ch = (Node) e.o;
                ch.indentUnit = unit;

                sb.append(pad).append(ch.name).append(":");

                boolean hadInline = false;
                for (Comment c : ch.inlineComments)
                    if (c.type == CommentType.START_BRANCH) {
                        String[] lines = c.text.split("\\R");
                        sb.append(" #").append(lines[0]).append("\n");
                        for (int i = 1; i < lines.length; i++)
                            sb.append(pad)
                                    .append(" ".repeat(unit))
                                    .append("#")
                                    .append(lines[i])
                                    .append("\n");
                        hadInline = true;
                    }

                if (!hadInline) sb.append("\n");

                sb.append(ch.toStringYaml(depth + 1));

                for (Comment c : ch.inlineComments)
                    if (c.type == CommentType.END_BRANCH) {
                        String[] lines = c.text.split("\\R");
                        for (String line : lines)
                            sb.append(pad).append("#").append(line).append("\n");
                    }
            }
        }

        if (depth == 0) {
            int len = sb.length();
            while (len > 0 && sb.charAt(len - 1) == '\n')
                len--;

            sb.setLength(len);

            if (endsWithNewline)
                sb.append('\n');
        }

        return sb.toString();
    }

    /**
     * Serializes this node as a top-level YAML document.
     * <p>
     * This is equivalent to calling {@link #toStringYaml(int)} with
     * a depth of {@code 0}.
     *
     * @return the YAML representation of this node
     */
    public String toStringYaml() {
        return toStringYaml(0);
    }

    /**
     * Writes this node to a file.
     *
     * @param file File to write at
     */
    public void save(@NotNull File file) {
        save(file.toPath());
    }

    /**
     * Writes this node to a path.
     *
     * @param path Path to write at
     */
    public void save(@NotNull Path path) {
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            Files.writeString(path, toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}