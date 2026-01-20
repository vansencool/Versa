package net.vansen.versa.comments;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a comment stored inside a node or attached to values/branches.
 * Can be either using `//` or `#` depending on {@link #slash}.
 */
public class Comment {

    /**
     * Comment category such as inline, branch start/end or standalone line.
     */
    public CommentType type;

    /**
     * Raw comment text content without prefix symbols.
     */
    public String text;

    /**
     * Absolute indentation level of this comment, measured in spaces.
     * <p>
     * This value is used primarily by indentation-sensitive formats
     * such as YAML, where standalone comments must preserve their
     * original column position.
     * <p>
     * Notes:<br>
     * • This value represents raw space count, not logical depth.<br>
     * • Inline comments typically ignore this field and are rendered
     *   relative to the element they are attached to.<br>
     * • Versa serialization ignores this value, while YAML
     *   serialization relies on it for accurate formatting.
     */
    public int indent;

    /**
     * True = print using //, false = print using #
     */
    public boolean slash;

    /**
     * Creates a comment defaulting to // format.
     *
     * @param t type of comment
     * @param s comment text (nullable for empty/omitted)
     */
    public Comment(@NotNull CommentType t, @Nullable String s) {
        type = t;
        text = s;
        slash = true;
    }

    /**
     * Creates a comment with explicit prefix format.
     *
     * @param t  type of comment
     * @param s  comment text (nullable for empty/omitted)
     * @param sl true = use // prefix, false = use #
     */
    public Comment(@NotNull CommentType t, @Nullable String s, boolean sl) {
        type = t;
        text = s;
        slash = sl;
    }

    /**
     * Converts this comment into formatted representation.
     *
     * @return formatted comment, or empty string when no text exists
     */
    public @NotNull String toString() {
        return (text == null || text.isEmpty()) ? "" : (slash ? " //" : " #") + text;
    }
}