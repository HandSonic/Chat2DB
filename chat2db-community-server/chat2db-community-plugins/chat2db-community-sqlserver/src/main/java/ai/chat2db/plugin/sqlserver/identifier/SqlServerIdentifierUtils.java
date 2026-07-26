package ai.chat2db.plugin.sqlserver.identifier;

import java.util.regex.Pattern;

public final class SqlServerIdentifierUtils {

    private static final Pattern COLLATION_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]+");

    private SqlServerIdentifierUtils() {
    }

    public static String quoteIdentifierPart(String identifier) {
        return "[" + escapeIdentifier(identifier) + "]";
    }

    public static String escapeIdentifier(String identifier) {
        return identifier == null ? "" : identifier.replace("]", "]]");
    }

    public static String escapeStringLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    public static String validateCollation(String collation) {
        if (collation == null || !COLLATION_NAME_PATTERN.matcher(collation).matches()) {
            throw new IllegalArgumentException("Invalid SQL Server collation name: " + collation);
        }
        return collation;
    }
}
