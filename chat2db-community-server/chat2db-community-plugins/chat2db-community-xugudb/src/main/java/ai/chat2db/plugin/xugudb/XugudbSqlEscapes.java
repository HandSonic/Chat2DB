package ai.chat2db.plugin.xugudb;

public final class XugudbSqlEscapes {

    private XugudbSqlEscapes() {
    }

    public static String escapeSqlLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

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
