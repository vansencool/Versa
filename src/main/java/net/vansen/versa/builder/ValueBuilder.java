package net.vansen.versa.builder;

import net.vansen.versa.comments.Comment;
import net.vansen.versa.comments.CommentType;
import net.vansen.versa.node.Node;
import net.vansen.versa.node.Value;
import net.vansen.versa.node.value.ValueType;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Builds a {@link Value} in a simple fluent format.
 * Set a name, assign a value type, optionally add inline comments,
 *
 * <pre>
 * Value v = ValueBuilder.builder()
 *     .name("enabled")
 *     .bool(true)
 *     .comment(CommentType.INLINE_VALUE, "Toggle system feature")
 *     .build();
 * </pre>
 */
@SuppressWarnings("unused")
public class ValueBuilder {
    private Value val = new Value();


    /**
     * Creates a new builder instance.
     *
     * @return a fresh {@link ValueBuilder}
     */
    public static ValueBuilder builder() {
        return new ValueBuilder();
    }

    /**
     * Creates a new builder instance from a provided Value.
     *
     * @param val the value to create a ValueBuilder from
     * @return a fresh {@link ValueBuilder} from the provided value.
     */
    public static ValueBuilder builder(Value val) {
        ValueBuilder builder = new ValueBuilder();
        builder.val = val;
        return builder;
    }

    /**
     * Sets the key/name of the value.
     *
     * @param n the value name/key
     * @return this builder
     */
    public ValueBuilder name(String n) {
        val.name = n;
        return this;
    }

    /**
     * Sets this value to a boolean type.
     *
     * @param b the boolean content
     * @return this builder
     */
    public ValueBuilder bool(boolean b) {
        val.type = ValueType.BOOL;
        val.iv = b ? 1 : 0;
        return this;
    }

    /**
     * Sets this value to an integer (auto-stored as INT).
     *
     * @param i the number
     * @return this builder
     */
    public ValueBuilder intVal(long i) {
        val.type = ValueType.INT;
        val.iv = i;
        return this;
    }

    /**
     * Sets this value to a long.
     *
     * @param l the long number
     * @return this builder
     */
    public ValueBuilder longVal(long l) {
        val.type = ValueType.LONG;
        val.iv = l;
        return this;
    }

    /**
     * Sets this value to a float (stored internally as double).
     *
     * @param f the float value
     * @return this builder
     */
    public ValueBuilder floatVal(double f) {
        val.type = ValueType.FLOAT;
        val.dv = f;
        return this;
    }

    /**
     * Sets this value to a double.
     *
     * @param d the number
     * @return this builder
     */
    public ValueBuilder doubleVal(double d) {
        val.type = ValueType.DOUBLE;
        val.dv = d;
        return this;
    }

    /**
     * Sets this value to a string.
     *
     * @param s the text
     * @return this builder
     */
    public ValueBuilder string(String s) {
        val.type = ValueType.STRING;
        val.sv = s;
        return this;
    }

    /**
     * Creates a LIST type value using other {@link Value} elements.
     *
     * @param vs values that the list should contain
     * @return this builder
     */
    public ValueBuilder list(Value... vs) {
        val.type = ValueType.LIST;
        val.list = new ArrayList<>();
        Collections.addAll(val.list, vs);
        return this;
    }

    /**
     * Creates a LIST_OF_BRANCHES type value using child {@link Node} entries.
     *
     * @param ns nested branch nodes
     * @return this builder
     */
    public ValueBuilder branches(Node... ns) {
        val.type = ValueType.LIST_OF_BRANCHES;
        val.branchList = new ArrayList<>();
        Collections.addAll(val.branchList, ns);
        return this;
    }

    /**
     * Adds a comment to the value.
     * Only {@link CommentType#INLINE_VALUE} appears inline when printed.
     *
     * @param t    comment type
     * @param text text content (no slashes or # required)
     * @return this builder
     */
    public ValueBuilder comment(CommentType t, String text) {
        val.comments.add(new Comment(t, text));
        return this;
    }

    /**
     * Returns the constructed {@link Value}, can be called multiple times.
     *
     * @return built Value instance
     */
    public Value build() {
        return val;
    }
}