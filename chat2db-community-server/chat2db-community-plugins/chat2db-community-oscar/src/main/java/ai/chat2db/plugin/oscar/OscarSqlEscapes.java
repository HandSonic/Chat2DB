package ai.chat2db.plugin.oscar;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

/**
 * Canonical escaping/quoting helpers for values interpolated into Oscar SQL text (#1914).
 */
public final class OscarSqlEscapes {

    private static final Pattern NUMERIC_DEFAULT_PATTERN = Pattern.compile(
            "^[+-]?(\\d+(\\.\\d+)?|\\.\\d+)([eE][+-]?\\d+)?$");
    private static final Pattern QUOTED_STRING_DEFAULT_PATTERN = Pattern.compile("^'([^']|'')*'$");
    private static final Pattern KEYWORD_DEFAULT_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_$]*$");
    private static final Pattern FUNCTION_CALL_DEFAULT_PATTERN = Pattern.compile(
            "^[A-Za-z_][A-Za-z0-9_$]*\\s*\\((?:[A-Za-z0-9_$.\\s,]|'(?:[^']|'')*')*\\)$");

    private OscarSqlEscapes() {
    }

    /**
     * Escapes a value interpolated into a single-quoted Oscar string literal
     * (surrounding quotes NOT added) by doubling every single quote.
     */
    public static String escapeSqlLiteral(String value) {
        if (value == null) {
            return null;
        }
        return StringUtils.replace(value, "'", "''");
    }

    /**
     * Quotes an identifier with double quotes: strips one surrounding double-quote
     * pair, then doubles every embedded double quote.
     */
    public static String quoteIdentifier(String identifier) {
        if (StringUtils.isBlank(identifier)) {
            return identifier;
        }
        String stripped = identifier;
        if (stripped.length() >= 2 && stripped.startsWith("\"") && stripped.endsWith("\"")) {
            stripped = stripped.substring(1, stripped.length() - 1);
        }
        return "\"" + StringUtils.replace(stripped, "\"", "\"\"") + "\"";
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
