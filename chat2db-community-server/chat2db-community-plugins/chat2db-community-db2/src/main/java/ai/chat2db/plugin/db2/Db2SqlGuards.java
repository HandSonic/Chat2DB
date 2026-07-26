package ai.chat2db.plugin.db2;

import java.util.regex.Pattern;

/**
 * Validation helpers for non-escapable SQL positions in DB2 DDL generation
 * (column default expressions, length units, fallback column type names and
 * index column sort directions). Escaping itself lives in
 * {@link ai.chat2db.plugin.db2.identifier.Db2IdentifierProcessor}.
 */
public final class Db2SqlGuards {

    /**
     * Conservative allow-list for unquoted column default expressions
     * (e.g. {@code 42}, {@code -1}, {@code CURRENT_TIMESTAMP}). Excludes quotes,
     * parentheses and commas so a default value cannot break out of the column
     * definition; comment markers are rejected separately.
     */
    private static final Pattern DEFAULT_VALUE_PATTERN = Pattern.compile("\\A[A-Za-z0-9_+/: \\t.-]+\\z");

    /**
     * Conservative allow-list for DB2 length units (e.g. {@code OCTETS}).
     */
    private static final Pattern UNIT_PATTERN = Pattern.compile("\\A[A-Za-z0-9_]+\\z");

    /**
     * Strict shape for fallback column types that are not exact enum matches
     * (e.g. {@code VARCHAR(10)}): letters only, with an optional numeric
     * size/scale suffix.
     */
    private static final Pattern FALLBACK_COLUMN_TYPE_PATTERN = Pattern.compile("\\A[A-Za-z]+(\\(\\d+(,\\d+)?\\))?\\z");

    private Db2SqlGuards() {
    }

    /**
     * Validates an unquoted column default expression before it is embedded into
     * generated DDL. Returns the value unchanged when it matches the allow-list
     * and carries no comment markers; throws otherwise (fail closed).
     */
    public static String requireDefaultExpression(String defaultValue) {
        if (!DEFAULT_VALUE_PATTERN.matcher(defaultValue).matches() || defaultValue.contains("--")
                || defaultValue.contains("/*")) {
            throw new IllegalArgumentException("Invalid DB2 default value: " + defaultValue);
        }
        return defaultValue;
    }

    /**
     * Validates a length unit before it is embedded into a sized column type.
     * Returns the unit unchanged when it matches the allow-list; throws otherwise.
     */
    public static String requireUnit(String unit) {
        if (!UNIT_PATTERN.matcher(unit).matches()) {
            throw new IllegalArgumentException("Invalid DB2 length unit: " + unit);
        }
        return unit;
    }

    /**
     * Validates a fallback column type expression (a type name that does not match
     * an enum constant, e.g. {@code VARCHAR(10)}) before it is embedded into
     * generated DDL. Returns the type unchanged when it matches the strict shape;
     * throws otherwise.
     */
    public static String requireColumnTypeExpression(String columnType) {
        if (columnType == null || !FALLBACK_COLUMN_TYPE_PATTERN.matcher(columnType).matches()) {
            throw new IllegalArgumentException("Invalid DB2 column type: " + columnType);
        }
        return columnType;
    }

    /**
     * Validates an index column sort direction against the ASC/DESC whitelist.
     * Returns the direction unchanged when whitelisted; throws otherwise.
     */
    public static String requireSortDirection(String ascOrDesc) {
        if (!"ASC".equalsIgnoreCase(ascOrDesc) && !"DESC".equalsIgnoreCase(ascOrDesc)) {
            throw new IllegalArgumentException("Invalid DB2 index column ordering: " + ascOrDesc);
        }
        return ascOrDesc;
    }
}
