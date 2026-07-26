package ai.chat2db.plugin.oceanbase.oracle;

public final class OceanbaseOracleSqlEscapes {

    private OceanbaseOracleSqlEscapes() {
    }

    public static String escapeSqlLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    public static String quoteIdentifier(String identifier) {
        String unquoted = identifier == null ? "" : stripSurroundingQuotes(identifier);
        return "\"" + unquoted.replace("\"", "\"\"") + "\"";
    }

    private static String stripSurroundingQuotes(String identifier) {
        if (identifier.length() >= 2 && identifier.startsWith("\"") && identifier.endsWith("\"")) {
            return identifier.substring(1, identifier.length() - 1);
        }
        return identifier;
    }
}
