package net.vansen.versa.annotations;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Adds a comment to a configuration value.
 * - Inline (`inline=true`) appends comment to the same line
 * - Block (`inline=false`) inserts comment on its own line above the key
 *
 * <pre>{@code
 * @ConfigPath("port")
 * @ConfigComment("Listening port")
 * public static int port = 22;
 *
 * @ConfigPath("host")
 * @ConfigComment(value="Local bind address", inline=true)
 * public static String host = "127.0.0.1";
 * }</pre>
 * <p>
 * Output:
 * <pre>
 * # Listening port
 * port = 22
 * host = "127.0.0.1" # Local bind address
 * </pre>
 */
@Repeatable(ConfigComments.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigComment {

    /**
     * Text for the comment.
     */
    @NotNull String value();

    /**
     * Whether comment appears inline (`key = value // comment`).
     * If false, comment is placed on a separate line above.
     */
    boolean inline() default false;
}