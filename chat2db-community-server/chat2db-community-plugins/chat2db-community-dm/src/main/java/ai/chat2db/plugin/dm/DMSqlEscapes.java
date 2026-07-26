package ai.chat2db.plugin.dm;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * Escaping helpers for DM SQL text: single-quoted string literals and
 * double-quoted identifiers.
 */
public final class DMSqlEscapes {

    /**
     * Legitimate column DEFAULT expressions: quoted string literals (with ''
     * escapes), numeric literals, or keyword/function forms such as
     * CURRENT_TIMESTAMP, SYSDATE, USER, SEQ.NEXTVAL. Anything else is rejected
     * because DEFAULT values are emitted verbatim.
     */
    private static final Pattern DEFAULT_EXPRESSION = Pattern.compile(
            "'([^']|'')*'|[+-]?(\\d+(\\.\\d+)?|\\.\\d+)|[A-Za-z_][A-Za-z0-9_]*([.][A-Za-z_][A-Za-z0-9_]*)*(\\s*\\([^;)]*\\))?");

    private DMSqlEscapes() {
    }

    /**
     * Escapes a value interpolated into a single-quoted SQL string literal by
     * doubling every single quote.
     */
    public static String escapeSqlLiteral(String value) {
        return StringUtils.replace(value, "'", "''");
    }

    /**
     * Escapes identifier content for a position already surrounded by double
     * quotes: strips one surrounding quote pair, then doubles every embedded
     * double quote.
     */
    public static String escapeIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        String unquoted = identifier;
        if (unquoted.length() >= 2 && unquoted.startsWith("\"") && unquoted.endsWith("\"")) {
            unquoted = unquoted.substring(1, unquoted.length() - 1);
        }
        return unquoted.replace("\"", "\"\"");
    }

    /**
     * Quotes an identifier with double quotes, doubling every embedded double
     * quote.
     */
    public static String quoteIdentifier(String identifier) {
        return "\"" + escapeIdentifier(identifier) + "\"";
    }

    /**
     * Validates a column DEFAULT expression that is emitted verbatim into DDL.
     * Accepts quoted string literals (escaped via doubling), numeric literals,
     * and keyword/function forms; rejects everything else.
     */
    public static String requireDefaultExpression(String defaultValue) {
        String trimmed = defaultValue.trim();
        if (!DEFAULT_EXPRESSION.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid DM default expression: " + defaultValue);
        }
        return trimmed;
    }
}
