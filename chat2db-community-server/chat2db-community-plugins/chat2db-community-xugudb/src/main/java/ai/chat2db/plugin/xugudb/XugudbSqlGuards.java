package ai.chat2db.plugin.xugudb;

import java.util.regex.Pattern;

import ai.chat2db.plugin.xugudb.identifier.XugudbIdentifierProcessor;

/**
 * Validation helpers for non-escapable SQL positions in XUGUDB DDL generation
 * (column default expressions and length units supplied through table metadata).
 * Escaping itself lives in {@link XugudbIdentifierProcessor}.
 */
public final class XugudbSqlGuards {

    /**
     * Conservative allow-list for unquoted column default expressions: numbers,
     * single-quoted literals with correctly doubled inner quotes, and identifiers
     * or function calls whose arguments are drawn from a safe character set.
     * Anything else is rejected so a hostile default cannot break out of the DDL statement.
     */
    private static final Pattern DEFAULT_VALUE_PATTERN = Pattern.compile(
            "^(-?\\d+(\\.\\d+)?|'([^']|'')*'|[A-Za-z_][A-Za-z0-9_]*(\\(([A-Za-z0-9_ ,.\\-+]|'([^']|'')*')*\\))?)$");

    /**
     * Conservative allow-list for length units (e.g. {@code BYTE}, {@code CHAR}).
     */
    private static final Pattern UNIT_PATTERN = Pattern.compile("^[A-Za-z]+$");

    private XugudbSqlGuards() {
    }

    /**
     * Validates a column default expression before it is embedded into generated DDL.
     * Returns the trimmed default unchanged when it matches the allow-list; throws
     * otherwise (fail closed).
     */
    public static String requireDefaultValue(String defaultValue) {
        String trimmed = defaultValue.trim();
        if (!DEFAULT_VALUE_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Unsupported column default value: " + defaultValue);
        }
        return trimmed;
    }

    /**
     * Validates a length unit before it is embedded into generated DDL. Returns the
     * trimmed unit unchanged when it matches the allow-list; throws otherwise
     * (fail closed).
     */
    public static String requireUnit(String unit) {
        String trimmed = unit.trim();
        if (!UNIT_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Unsupported length unit: " + unit);
        }
        return trimmed;
    }
}
