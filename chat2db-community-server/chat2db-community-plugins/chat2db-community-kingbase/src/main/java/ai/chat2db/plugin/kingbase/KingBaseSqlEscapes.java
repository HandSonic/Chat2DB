package ai.chat2db.plugin.kingbase;

import org.apache.commons.lang3.StringUtils;

/**
 * Escaping/validation helpers for SQL fragments built by the kingbase plugin (#1914).
 */
public final class KingBaseSqlEscapes {

    private KingBaseSqlEscapes() {
    }

    /**
     * Escape a value interpolated into a single-quoted SQL string literal by doubling every single quote.
     */
    public static String escapeSqlLiteral(String value) {
        if (value == null) {
            return null;
        }
        return StringUtils.replace(value, "'", "''");
    }

    /**
     * Escape the inner content of a double-quoted identifier: strip one surrounding quote pair if present,
     * then double every embedded double-quote. The result is NOT wrapped in quotes.
     */
    public static String escapeIdentifier(String identifier) {
        if (StringUtils.isBlank(identifier)) {
            return identifier;
        }
        String inner = identifier;
        if (inner.length() >= 2 && inner.startsWith("\"") && inner.endsWith("\"")) {
            inner = inner.substring(1, inner.length() - 1);
        }
        return StringUtils.replace(inner, "\"", "\"\"");
    }

    /**
     * Quote a value for a double-quoted identifier position, doubling embedded double-quotes.
     */
    public static String quoteIdentifier(String identifier) {
        if (StringUtils.isBlank(identifier)) {
            return identifier;
        }
        return "\"" + escapeIdentifier(identifier) + "\"";
    }

    /**
     * Validate a non-escapable SQL expression position (DEFAULT expressions, ENCODING values, ...)
     * and return it unchanged. Quoted string literals and quoted identifiers inside the expression
     * are honored so legitimate values such as {@code 'it''s'} or {@code now()} are accepted.
     *
     * @throws IllegalArgumentException if the expression contains characters that could terminate
     *                                  or comment out the surrounding statement
     */
    public static String requireSafeExpression(String value, String description) {
        if (!isSafeSqlExpression(value)) {
            throw new IllegalArgumentException("Unsafe SQL expression for " + description + ": " + value);
        }
        return value;
    }

    /**
     * Validate an index access method name emitted verbatim after USING. Only plain identifier
     * characters are legal (btree, hash, gist, gin, spgist, brin, ...).
     */
    public static String requireIndexMethod(String method) {
        if (method == null || !method.matches("[A-Za-z][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid index method: " + method);
        }
        return method;
    }

    /**
     * Quote-aware whitelist check for non-escapable SQL expression positions. Content inside
     * single-quoted literals (with '' escapes) and double-quoted identifiers (with "" escapes) is
     * allowed; outside quotes only expression characters are permitted and statement terminators
     * or comment tokens are rejected.
     */
    public static boolean isSafeSqlExpression(String value) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        int length = value.length();
        for (int i = 0; i < length; i++) {
            char c = value.charAt(i);
            if (inSingleQuote) {
                if (c == '\'') {
                    if (i + 1 < length && value.charAt(i + 1) == '\'') {
                        i++;
                    } else {
                        inSingleQuote = false;
                    }
                }
                continue;
            }
            if (inDoubleQuote) {
                if (c == '"') {
                    if (i + 1 < length && value.charAt(i + 1) == '"') {
                        i++;
                    } else {
                        inDoubleQuote = false;
                    }
                }
                continue;
            }
            if (c == '\'') {
                inSingleQuote = true;
                continue;
            }
            if (c == '"') {
                inDoubleQuote = true;
                continue;
            }
            if (c == ';') {
                return false;
            }
            if (c == '-' && i + 1 < length && value.charAt(i + 1) == '-') {
                return false;
            }
            if (c == '/' && i + 1 < length && value.charAt(i + 1) == '*') {
                return false;
            }
            if (c == '*' && i + 1 < length && value.charAt(i + 1) == '/') {
                return false;
            }
            if (!Character.isLetterOrDigit(c) && !Character.isWhitespace(c)
                    && "_.()+*/,-<>=:@%[]".indexOf(c) < 0) {
                return false;
            }
        }
        return !inSingleQuote && !inDoubleQuote;
    }
}
