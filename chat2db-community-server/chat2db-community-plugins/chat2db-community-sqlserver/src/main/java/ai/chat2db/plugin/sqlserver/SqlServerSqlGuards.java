package ai.chat2db.plugin.sqlserver;

import java.util.regex.Pattern;

import ai.chat2db.plugin.sqlserver.identifier.SqlServerIdentifierProcessor;

/**
 * Validation helpers for non-escapable SQL positions in SQL Server DDL
 * generation (collation names embedded as bare tokens).
 * Escaping itself lives in {@link SqlServerIdentifierProcessor}.
 */
public final class SqlServerSqlGuards {

    /**
     * Conservative allow-list for collation names reported by JDBC metadata or
     * user input before they are embedded as bare tokens in generated DDL.
     */
    private static final Pattern COLLATION_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]+");

    private SqlServerSqlGuards() {
    }

    /**
     * Validates a collation name before it is embedded into generated DDL.
     * Returns the collation unchanged when it matches the allow-list; throws
     * otherwise (fail closed).
     */
    public static String validateCollation(String collation) {
        if (collation == null || !COLLATION_NAME_PATTERN.matcher(collation).matches()) {
            throw new IllegalArgumentException("Invalid SQL Server collation name: " + collation);
        }
        return collation;
    }
}
