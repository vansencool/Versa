package net.vansen.versa.builder;

import net.vansen.versa.comments.Comment;
import net.vansen.versa.comments.CommentType;
import net.vansen.versa.node.Node;
import net.vansen.versa.node.Value;
import net.vansen.versa.node.entry.Entry;
import net.vansen.versa.node.entry.EntryType;
import net.vansen.versa.node.insert.InsertPoint;
import org.jetbrains.annotations.NotNull;

/**
 * Builds a {@link Node} (configuration branch).
 * Nodes store values, child nodes, comments, blank lines, and formatting order.
 * <p>
 * Example full config construction:
 * <pre><code>
 * Node config = new NodeBuilder()
 *     .name("database")
 *     .comment("Connection settings")
 *     .add(new ValueBuilder().name("host").string("localhost"))
 *     .add(new ValueBuilder().name("port").intVal(3306))
 *     .emptyLine()
 *     .child(
 *         new NodeBuilder()
 *             .name("pool")
 *             .add(new ValueBuilder().name("size").intVal(10))
 *             .build()
 *     )
 *     .build();
 *
 * System.out.println(config);
 * </code></pre>
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class NodeBuilder {
    private Node n = new Node();

    /**
     * Creates a new empty node builder.
     *
     * @return new {@link NodeBuilder}
     */
    public static @NotNull NodeBuilder builder() {
        return new NodeBuilder();
    }

    /**
     * Creates a builder wrapping an existing {@link Node}.
     * Useful for modification or merging.
     *
     * @param n source node
     * @return builder operating on that node
     */
    public static @NotNull NodeBuilder fromNode(@NotNull Node n) {
        NodeBuilder b = new NodeBuilder();
        b.n = n;
        return b;
    }

    /**
     * Sets the branch/node name.
     *
     * @param name name of this node
     * @return this builder
     */
    public @NotNull NodeBuilder name(@NotNull String name) {
        n.name = name;
        return this;
    }

    /**
     * Adds a completed {@link Value} entry into the node.
     *
     * @param v value instance
     * @return this builder
     */
    public @NotNull NodeBuilder add(@NotNull Value v) {
        n.values.put(v.name, v);
        n.order.add(new Entry(EntryType.VALUE, v));
        return this;
    }

    /**
     * Convenience method using {@link ValueBuilder}.
     *
     * @param vb value builder to build and insert
     * @return this builder
     */
    public @NotNull NodeBuilder add(@NotNull ValueBuilder vb) {
        return add(vb.build());
    }

    /**
     * Adds a nested child {@link Node}.
     *
     * @param c child node
     * @return this builder
     */
    public @NotNull NodeBuilder child(@NotNull Node c) {
        n.children.add(c);
        n.order.add(new Entry(EntryType.BRANCH, c));
        return this;
    }

    /**
     * Convenience overload for child using {@link NodeBuilder}.
     *
     * @param b builder that will be built into node
     * @return this builder
     */
    public @NotNull NodeBuilder child(@NotNull NodeBuilder b) {
        return child(b.build());
    }

    /**
     * Adds a standalone `//` comment line.
     * A leading space in text determines spacing after //.
     *
     * @param text comment content
     * @return this builder
     */
    public @NotNull NodeBuilder comment(@NotNull String text) {
        Comment c = new Comment(CommentType.COMMENT_LINE, text, true);
        n.order.add(new Entry(EntryType.COMMENT, c));
        return this;
    }

    /**
     * Adds a standalone `#` comment line.
     * A leading space in text determines spacing after #.
     *
     * @param text comment content
     * @return this builder
     */
    public @NotNull NodeBuilder commentHash(@NotNull String text) {
        Comment c = new Comment(CommentType.COMMENT_LINE, text, false);
        n.order.add(new Entry(EntryType.COMMENT, c));
        return this;
    }

    /**
     * Adds an inline `//` comment at the opening `{`.
     * Appears as: `name { // comment`
     *
     * @param text comment content
     * @return this builder
     */
    public @NotNull NodeBuilder branchStartComment(@NotNull String text) {
        n.inlineComments.add(new Comment(CommentType.START_BRANCH, text, true));
        return this;
    }

    /**
     * Adds an inline `#` comment at `{`.
     * Appears as: `name { #comment`
     *
     * @param text comment content
     * @return this builder
     */
    public @NotNull NodeBuilder branchStartCommentHash(@NotNull String text) {
        n.inlineComments.add(new Comment(CommentType.START_BRANCH, text, false));
        return this;
    }

    /**
     * Adds an inline `//` comment after `}`.
     * Appears as: `} // comment`
     *
     * @param text comment content
     * @return this builder
     */
    public @NotNull NodeBuilder branchEndComment(@NotNull String text) {
        n.inlineComments.add(new Comment(CommentType.END_BRANCH, text, true));
        return this;
    }

    /**
     * Adds an inline `#` comment after `}`.
     * Appears as: `} #comment`
     *
     * @param text comment content
     * @return this builder
     */
    public @NotNull NodeBuilder branchEndCommentHash(@NotNull String text) {
        n.inlineComments.add(new Comment(CommentType.END_BRANCH, text, false));
        return this;
    }

    /**
     * Creates an insertion point before a value matching this key.
     * If the key does not exist, insertion defaults to the end.
     *
     * @param key value name to search for
     * @return insertion point for adding comments, values or blanks
     */
    public @NotNull InsertPoint before(@NotNull String key) {
        return n.before(key);
    }

    /**
     * Creates an insertion point after a value matching this key.
     * If the key does not exist, insertion defaults to the end.
     *
     * @param key value name to search
     * @return insertion point directly after the matched value
     */
    public @NotNull InsertPoint after(@NotNull String key) {
        return n.after(key);
    }

    /**
     * Creates an insertion point before a branch with this name.
     * If the branch does not exist, insertion defaults to the end.
     *
     * @param name branch name
     * @return insertion point targeting the matching branch start
     */
    public @NotNull InsertPoint beforeBranch(@NotNull String name) {
        return n.beforeBranch(name);
    }

    /**
     * Creates an insertion point after a branch with this name.
     * If the branch does not exist, insertion defaults to the end.
     *
     * @param name target branch name
     * @return insertion point placed right after the branch
     */
    public @NotNull InsertPoint afterBranch(@NotNull String name) {
        return n.afterBranch(name);
    }

    /**
     * Inserts a blank line for formatting.
     *
     * @return this builder
     */
    public @NotNull NodeBuilder emptyLine() {
        n.emptyLine();
        return this;
    }

    /**
     * Builds the final {@link Node}.
     *
     * @return constructed node
     */
    public @NotNull Node build() {
        return n;
    }
}