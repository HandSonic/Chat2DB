package ai.chat2db.plugin.oscar;

import java.util.regex.Pattern;

import ai.chat2db.plugin.oscar.identifier.OscarIdentifierProcessor;
import org.apache.commons.lang3.StringUtils;

/**
 * Validation helpers for non-escapable SQL positions in Oscar DDL generation
 * (column default expressions, CHAR/VARCHAR length units, index sort orders).
 * Escaping itself lives in {@link OscarIdentifierProcessor}.
 */
public final class OscarSqlGuards {

    private static final Pattern NUMERIC_DEFAULT_PATTERN = Pattern.compile(
            "^[+-]?(\\d+(\\.\\d+)?|\\.\\d+)([eE][+-]?\\d+)?$");
    // Unrolled-loop forms ([^']*(?:''[^']*)*) are used instead of ([^']|'')* to avoid
    // regex backtracking (CodeQL polynomial-regex alert); they accept the same language.
    private static final Pattern QUOTED_STRING_DEFAULT_PATTERN = Pattern.compile("^'[^']*(?:''[^']*)*'$");
    private static final Pattern KEYWORD_DEFAULT_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_$]*$");
    private static final Pattern FUNCTION_CALL_DEFAULT_PATTERN = Pattern.compile(
            "^[A-Za-z_][A-Za-z0-9_$]*\\s*\\((?:[A-Za-z0-9_$.\\s,]|'[^']*(?:''[^']*)*')*\\)$");

    private OscarSqlGuards() {
    }

    /**
     * Validates a raw DEFAULT expression emitted verbatim into DDL (positions where
     * quoting would change semantics). Accepts numeric literals, single-quoted string
     * literals (with '' escapes), plain keywords such as SYSDATE/CURRENT_TIMESTAMP,
     * and simple function calls such as sys_guid() or to_date('2024-01-01','YYYY-MM-DD').
     */
    public static String requireDefaultValueExpression(String defaultValue) {
        if (StringUtils.isBlank(defaultValue)) {
            throw new IllegalArgumentException("Invalid Oscar default value: " + defaultValue);
        }
        String value = defaultValue.trim();
        if (NUMERIC_DEFAULT_PATTERN.matcher(value).matches()
                || QUOTED_STRING_DEFAULT_PATTERN.matcher(value).matches()
                || KEYWORD_DEFAULT_PATTERN.matcher(value).matches()
                || FUNCTION_CALL_DEFAULT_PATTERN.matcher(value).matches()) {
            return value;
        }
        throw new IllegalArgumentException("Invalid Oscar default value: " + defaultValue);
    }

    /**
     * Validates a CHAR/VARCHAR length unit (BYTE/CHAR) emitted verbatim into DDL.
     */
    public static String requireLengthUnit(String unit) {
        if (StringUtils.isBlank(unit)) {
            throw new IllegalArgumentException("Invalid Oscar length unit: " + unit);
        }
        String value = unit.trim();
        if ("BYTE".equalsIgnoreCase(value) || "CHAR".equalsIgnoreCase(value)) {
            return value;
        }
        throw new IllegalArgumentException("Invalid Oscar length unit: " + unit);
    }

    /**
     * Validates an index column sort order (ASC/DESC) emitted verbatim into DDL.
     */
    public static String requireSortOrder(String ascOrDesc) {
        if (StringUtils.isBlank(ascOrDesc)) {
            throw new IllegalArgumentException("Invalid Oscar sort order: " + ascOrDesc);
        }
        String value = ascOrDesc.trim();
        if ("ASC".equalsIgnoreCase(value) || "DESC".equalsIgnoreCase(value)) {
            return value;
        }
        throw new IllegalArgumentException("Invalid Oscar sort order: " + ascOrDesc);
    }
}
