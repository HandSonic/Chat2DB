package ai.chat2db.plugin.h2;

import org.apache.commons.lang3.StringUtils;

public final class H2SqlEscapes {

    private H2SqlEscapes() {
    }

    public static String escapeSqlLiteral(String value) {
        return value == null ? "" : StringUtils.replace(value, "'", "''");
    }

    public static String escapeIdentifier(String identifier) {
        if (identifier == null) {
            return "";
        }
        String stripped = identifier;
        if (stripped.length() >= 2 && stripped.startsWith("\"") && stripped.endsWith("\"")) {
            stripped = stripped.substring(1, stripped.length() - 1);
        }
        return StringUtils.replace(stripped, "\"", "\"\"");
    }

    public static String quoteIdentifier(String identifier) {
        return "\"" + escapeIdentifier(identifier) + "\"";
    }
}
