package ai.chat2db.plugin.sqlite;

import java.util.regex.Pattern;

import ai.chat2db.plugin.sqlite.identifier.SqliteIdentifierProcessor;
import org.apache.commons.lang3.StringUtils;

/**
 * Validation helpers for non-escapable SQL positions in SQLite DDL generation
 * (collation/charset keyword names, free-text column type names, column default
 * expressions) and for {@code --} line comments. Escaping itself lives in
 * {@link SqliteIdentifierProcessor}.
 */
public final class SqliteSqlGuards {

    /**
     * Conservative allow-list for names embedded into keyword positions where quoting
     * is not possible (COLLATE, CHARACTER SET). Accepts BINARY/NOCASE/RTRIM and
     * simple custom collation names; rejects anything that could break out of the DDL.
     */
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_]+");

    /**
     * Conservative allow-list for free-text column type names
     * (e.g. {@code VARCHAR(255)}, {@code NUMERIC(10,2)}, {@code DOUBLE PRECISION}).
     * Anything else is rejected so a hostile type cannot smuggle SQL into generated DDL.
     */
    private static final Pattern SAFE_TYPE_NAME = Pattern.compile("[A-Za-z0-9_(), ]+");

    /**
     * Conservative allow-list for unquoted column default expressions
     * (e.g. {@code 42}, {@code -1.5}, {@code CURRENT_TIMESTAMP}, {@code (1+2)}).
     * Excludes quotes and semicolons so an expression cannot break out of the DDL statement.
     */
    private static final Pattern SAFE_DEFAULT_EXPRESSION = Pattern.compile("[A-Za-z0-9_()., +\\-*/%]+");

    private SqliteSqlGuards() {
    }

    /**
     * Validates a name embedded into a keyword position (collation, charset) against a
     * conservative allow-list. Returns the name unchanged when safe; throws otherwise
     * (fail closed).
     *
     * @throws IllegalArgumentException if the name contains unexpected characters
     */
    public static String requireSafeName(String name, String what) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Unsafe " + what + " name: " + name);
        }
        return name;
    }

    /**
     * Validates a free-text column type name before it is embedded into generated DDL.
     * Returns the type name unchanged when it matches a conservative allow-list;
     * throws otherwise (fail closed).
     *
     * @throws IllegalArgumentException if the type name contains unexpected characters
     */
    public static String requireSafeTypeName(String typeName) {
        if (typeName != null && !SAFE_TYPE_NAME.matcher(typeName).matches()) {
            throw new IllegalArgumentException("Unsafe column type name: " + typeName);
        }
        return typeName;
    }

    /**
     * Renders a column default expression safe for inclusion in generated DDL:
     * <ul>
     *   <li>values wrapped in single quotes are treated as string literals: if the inner
     *   content is well-formed (quotes correctly doubled) it is passed through unchanged,
     *   otherwise the inner content is re-escaped;</li>
     *   <li>unquoted values matching a conservative expression allow-list (numbers,
     *   keywords, parenthesised expressions) are passed through unchanged;</li>
     *   <li>anything else is neutralised by rendering it as an escaped string literal.</li>
     * </ul>
     * Returns an empty string for {@code null}.
     */
    public static String escapeColumnDefault(String columnDefault) {
        if (columnDefault == null) {
            return "";
        }
        String trimmed = columnDefault.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("'") && trimmed.endsWith("'")) {
            String inner = trimmed.substring(1, trimmed.length() - 1);
            if (isWellFormedEscapedLiteral(inner)) {
                return trimmed;
            }
            return "'" + SqliteIdentifierProcessor.INSTANCE.escapeString(inner) + "'";
        }
        if (SAFE_DEFAULT_EXPRESSION.matcher(trimmed).matches()) {
            return trimmed;
        }
        return "'" + SqliteIdentifierProcessor.INSTANCE.escapeString(trimmed) + "'";
    }

    /**
     * Neutralises a comment embedded after a {@code --} line-comment marker by
     * flattening CR/LF to spaces, so the comment cannot break out onto a new,
     * executable line. Returns an empty string for {@code null}.
     */
    public static String sanitizeLineComment(String comment) {
        if (comment == null) {
            return "";
        }
        return StringUtils.replaceEach(comment, new String[]{"\r", "\n"}, new String[]{" ", " "});
    }

    /**
     * Checks that every single quote in the inner content of a quoted literal is
     * correctly doubled (the only escape mechanism SQLite uses inside string literals).
     */
    private static boolean isWellFormedEscapedLiteral(String inner) {
        for (int i = 0; i < inner.length(); i++) {
            if (inner.charAt(i) == '\'') {
                if (i + 1 >= inner.length() || inner.charAt(i + 1) != '\'') {
                    return false;
                }
                i++;
            }
        }
        return true;
    }
}
