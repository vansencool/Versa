package net.vansen.versa;

import net.vansen.fursconfig.file.FileTextReader;
import net.vansen.versa.language.Language;
import net.vansen.versa.node.Node;
import net.vansen.versa.parser.VersaParser;
import net.vansen.versa.parser.YamlParser;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;

@SuppressWarnings("unused")
public final class Versa {

    private Versa() {
    }

    /**
     * Parses configuration data from a raw text string.
     * <p>
     * The input language is automatically detected and the appropriate
     * parser is selected.
     *
     * @param text raw configuration text
     * @return parsed configuration node
     */
    public static @NotNull Node parseText(@NotNull String text) {
        Language lang = detectLanguage(text);

        if (lang == Language.YAML) return new YamlParser(text).parse();

        return new VersaParser(text).parse();
    }

    /**
     * Reads and parses configuration data from a file path string.
     *
     * @param file file path
     * @return parsed configuration node
     */
    public static @NotNull Node parse(@NotNull String file) {
        return parseText(FileTextReader.read(file));
    }

    /**
     * Reads and parses configuration data from a {@link File}.
     *
     * @param file configuration file
     * @return parsed configuration node
     */
    public static @NotNull Node parse(@NotNull File file) {
        return parseText(FileTextReader.read(file));
    }

    /**
     * Reads and parses configuration data from a {@link Path}.
     *
     * @param path configuration file path
     * @return parsed configuration node
     */
    public static @NotNull Node parse(@NotNull Path path) {
        return parseText(FileTextReader.read(path));
    }

    /**
     * Detects the configuration language used by the given text.
     * <p>
     * Detection is performed using a fast, single-pass lexical scan and
     * does not involve full parsing or allocation.
     * <p>
     * Detection rules:<br>
     * • If any Versa-only syntax is encountered (such as {@code =},
     *   unquoted {@code { }}, or {@code //} comments), the language is
     *   immediately classified as {@link Language#VERSA}.<br>
     * • If no Versa-only syntax is found, the input is treated as
     *   {@link Language#YAML} by default.
     *
     * @param s configuration text
     * @return the detected configuration language
     */
    public static @NotNull Language detectLanguage(@NotNull String s) {
        boolean inQ = false;
        boolean lineStart = true;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c == '\n' || c == '\r') {
                lineStart = true;
                continue;
            }

            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inQ = !inQ;
                lineStart = false;
                continue;
            }

            if (inQ) {
                lineStart = false;
                continue;
            }

            if (c == ' ' || c == '\t') {
                if (lineStart) continue;
            }

            if (c == '=')
                return Language.VERSA;

            if (c == '{' || c == '}')
                return Language.VERSA;

            if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/')
                return Language.VERSA;

            lineStart = false;
        }

        return Language.YAML;
    }
}