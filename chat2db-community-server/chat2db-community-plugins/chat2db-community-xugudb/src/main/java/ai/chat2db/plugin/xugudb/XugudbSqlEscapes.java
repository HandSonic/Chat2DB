package ai.chat2db.plugin.xugudb;

public final class XugudbSqlEscapes {

    private XugudbSqlEscapes() {
    }

    public static String escapeSqlLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    /**
     * Escapes an identifier for use inside double quotes by doubling embedded {@code "}.
     * <p>
     * Callers MUST pass raw (unescaped) identifier values. Do NOT pass text that has already
     * been escaped or quoted for SQL — it will be escaped again. For compatibility with
     * database metadata that arrives already wrapped in double quotes, a single pair of
     * surrounding {@code "} is stripped before escaping; an identifier that legitimately
     * begins and ends with a {@code "} character cannot be represented through this API.
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

    public static String quoteIdentifier(String identifier) {
        return "\"" + escapeIdentifier(identifier) + "\"";
    }
}
