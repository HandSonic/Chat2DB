package ai.chat2db.plugin.sundb;

/**
 * Escaping helpers for values interpolated into SQL text.
 * Mirrors Oracle's escapeSqlLiteral (#2052) and SqlServerIdentifierUtils (#2053).
 */
public final class SUNDBSqlEscapes {

    private SUNDBSqlEscapes() {
    }

    /**
     * Escapes a value placed inside a single-quoted SQL string literal
     * by doubling every single quote.
     */
    public static String escapeSqlLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    /**
     * Escapes an identifier placed inside double quotes by stripping any
     * surrounding double quotes and doubling every embedded double quote.
     */
    public static String escapeIdentifier(String identifier) {
        if (identifier == null) {
            return "";
        }
        String unquoted = identifier;
        if (unquoted.length() >= 2 && unquoted.startsWith("\"") && unquoted.endsWith("\"")) {
            unquoted = unquoted.substring(1, unquoted.length() - 1);
        }
        return unquoted.replace("\"", "\"\"");
    }

    /**
     * Escapes an identifier and wraps it in double quotes.
     */
    public static String quoteIdentifier(String identifier) {
        return "\"" + escapeIdentifier(identifier) + "\"";
    }

    /**
     * Validates an index sort direction: only ASC/DESC are legal, returned in
     * canonical uppercase. Anything else is rejected to block DDL injection.
     */
    public static String requireAscOrDesc(String value) {
        String trimmed = value == null ? "" : value.trim();
        if ("ASC".equalsIgnoreCase(trimmed)) {
            return "ASC";
        }
        if ("DESC".equalsIgnoreCase(trimmed)) {
            return "DESC";
        }
        throw new IllegalArgumentException("Invalid SUNDB index sort direction: " + value);
    }
}
