package net.vansen.versa.annotations;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Adds comments immediately **before and/or after** a branch block.
 * <p>
 * This annotation is applied on the <b>field that declares a branch</b>,
 * not on the branch class itself.
 *
 * <ul>
 *   <li>{@code start} is written directly <b>before</b> the branch</li>
 *   <li>{@code after} is written directly <b>after</b> the branch</li>
 * </ul>
 *
 * <pre>{@code
 * @Branch
 * @ConfigPath("database")
 * @ConfigBranchHeader(
 *     start = "Database configuration",
 *     after = "End of database section"
 * )
 * public static Database DATABASE = new Database();
 *
 * public static class Database {
 *     @ConfigPath("host") public String host = "localhost";
 *     @ConfigPath("port") public int port = 3306;
 * }
 * }</pre>
 *
 * <p>
 * Result:
 *
 * <pre>
 * # Database configuration
 * database {
 *     host = "localhost"
 *     port = 3306
 * }
 * # End of database section
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigBranchHeader {

    /**
     * Comment placed directly before the branch.
     */
    @NotNull String start() default "";

    /**
     * Whether to place the comment after the branch instead.
     */
    @NotNull String after() default "";
}
