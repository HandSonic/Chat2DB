package ai.chat2db.plugin.redshift;

import org.apache.commons.lang3.StringUtils;

/**
 * Escaping helpers for values and identifiers interpolated into Redshift SQL.
 */
public final class RedshiftSqlEscapes {

    private static final String DOUBLE_QUOTE = "\"";

    private RedshiftSqlEscapes() {
    }

    /**
     * Escapes a value interpolated into a single-quoted SQL string literal
     * by doubling every single quote.
     */
    public static String escapeSqlLiteral(String value) {
        return value == null ? null : StringUtils.replace(value, "'", "''");
    }

    /**
     * Escapes a double-quoted identifier by stripping one surrounding quote pair
     * and doubling every embedded double quote.
     */
    public static String escapeIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        String unquoted = identifier;
        if (unquoted.length() >= 2 && unquoted.startsWith(DOUBLE_QUOTE) && unquoted.endsWith(DOUBLE_QUOTE)) {
            unquoted = unquoted.substring(1, unquoted.length() - 1);
        }
        return StringUtils.replace(unquoted, "\"", "\"\"");
    }

    /**
     * Wraps an identifier in double quotes with embedded quotes doubled.
     */
    public static String quoteIdentifier(String identifier) {
        if (StringUtils.isBlank(identifier)) {
            return identifier;
        }
        return DOUBLE_QUOTE + escapeIdentifier(identifier) + DOUBLE_QUOTE;
    }
}
