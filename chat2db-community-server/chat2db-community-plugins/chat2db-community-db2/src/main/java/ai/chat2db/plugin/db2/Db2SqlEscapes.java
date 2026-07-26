package ai.chat2db.plugin.db2;

/**
 * Escaping helpers for values interpolated into DB2 SQL text.
 */
public final class Db2SqlEscapes {

    private Db2SqlEscapes() {
    }

    /**
     * Escapes a value placed inside a single-quoted SQL string literal by doubling single quotes.
     */
    public static String escapeSqlLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    /**
     * Escapes an identifier placed inside a double-quoted identifier position: strips any
     * surrounding double quotes, then doubles every embedded double quote.
     */
    public static String escapeIdentifier(String identifier) {
        if (identifier == null) {
            return "";
        }
        String stripped = identifier;
        if (stripped.length() >= 2 && stripped.startsWith("\"") && stripped.endsWith("\"")) {
            stripped = stripped.substring(1, stripped.length() - 1);
        }
        return stripped.replace("\"", "\"\"");
    }

    /**
     * Escapes an identifier and wraps it in double quotes for unquoted identifier positions.
     */
    public static String quoteIdentifier(String identifier) {
        return "\"" + escapeIdentifier(identifier) + "\"";
    }
}
