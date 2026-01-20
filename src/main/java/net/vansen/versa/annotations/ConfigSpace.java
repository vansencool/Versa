package net.vansen.versa.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controls spacing around a **scalar value** (not branch).
 *
 * <pre>{@code
 * @ConfigPath("enabled")
 * @ConfigSpace(before = true)        // blank line above
 * public static boolean enabled = true;
 *
 * @ConfigPath("timeout")
 * @ConfigSpace(after = true)         // blank line below
 * public static int timeout = 60;
 * }</pre>
 * <p>
 * Output:
 * <pre>
 *
 * enabled = true
 *
 * timeout = 60
 *
 * </pre>
 */
@Repeatable(ConfigSpaces.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigSpace {

    /**
     * Insert space **before this value**.
     */
    boolean before() default false;

    /**
     * Insert space **after this value**.
     */
    boolean after() default true;
}