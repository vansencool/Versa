package net.vansen.versa.language;

/**
 * Specifies the preferred configuration language to use when
 * serializing nodes back to text.
 * <p>
 * All configuration data is stored in a shared, mutable, runtime-editable
 * node model that is independent of any concrete syntax.
 * <p>
 * Because of this design, configurations can be parsed from one language
 * and emitted in another without loss of structure, comments, or layout
 * where the target language allows it.
 * <p>
 * Parsing may auto-detect the input language, but this value determines
 * the default output language unless explicitly overridden.
 */
public enum Language {

    /**
     * Versa configuration format.
     * <p>
     * A high-performance, feature-packed configuration language designed
     * for readability, flexibility, and safe runtime modification.
     * <p>
     * Characteristics:<br>
     * • Human-readable syntax with spaces allowed in keys.<br>
     * • Supports both {@code =} and {@code :} assignment.<br>
     * • Uses brace-based blocks for structure.<br>
     * • Supports {@code //} and {@code #} comments (inline and standalone).<br>
     * • Preserves formatting and comments when writing back to file.<br>
     * • Can be freely converted to and from other supported languages.
     */
    VERSA,

    /**
     * YAML configuration format.
     * <p>
     * An indentation-sensitive configuration language focused on
     * human readability.
     * <p>
     * Characteristics:<br>
     * • Structure is defined by indentation.<br>
     * • Uses {@code :} for key-value mapping.<br>
     * • Uses {@code -} for list items.<br>
     * • Supports {@code #} comments.<br>
     * • Preserves formatting and comments when writing back to file.<br>
     * • Can be freely converted to and from other supported languages.
     */
    YAML
}