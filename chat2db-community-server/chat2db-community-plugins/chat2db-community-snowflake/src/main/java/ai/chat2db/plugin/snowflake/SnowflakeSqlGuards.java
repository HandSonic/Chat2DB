package ai.chat2db.plugin.snowflake;

import java.util.regex.Pattern;

import ai.chat2db.plugin.snowflake.identifier.SnowflakeIdentifierProcessor;
import org.apache.commons.lang3.StringUtils;

/**
 * Validation helpers for non-escapable SQL positions in Snowflake DDL generation
 * (engine/charset/collation tokens, raw DEFAULT expressions, index sort direction).
 * Escaping itself lives in {@link SnowflakeIdentifierProcessor}.
 */
public final class SnowflakeSqlGuards {

    private static final Pattern SNOWFLAKE_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_$]+$");
    private static final Pattern DEFAULT_EXPRESSION_PATTERN = Pattern.compile(
            "^([-+]?(\\d+(\\.\\d+)?|\\.\\d+)([eE][+-]?\\d+)?|(?i:TRUE|FALSE)|[A-Za-z_][A-Za-z0-9_]*(\\s*\\([^;)]*\\))?)$");

    private SnowflakeSqlGuards() {
    }

    /**
     * Validate a strict Snowflake name token (ENGINE / CHARACTER SET / COLLATE style positions where
     * escaping is impossible by design).
     */
    public static String requireSnowflakeName(String value, String what) {
        if (value == null || !SNOWFLAKE_NAME_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid Snowflake " + what + ": " + value);
        }
        return value;
    }

    /**
     * Validate a raw DEFAULT expression (positions where quoting would change semantics). Quoted string
     * literals stay literals: the content is escaped and re-quoted. Otherwise only numeric/boolean
     * literals, bare keywords (e.g. CURRENT_TIMESTAMP) and simple function calls are accepted.
     */
    public static String requireDefaultExpression(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Invalid Snowflake default value: null");
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("'") && trimmed.endsWith("'")) {
            return "'" + SnowflakeIdentifierProcessor.INSTANCE.escapeString(trimmed.substring(1, trimmed.length() - 1)) + "'";
        }
        if (!DEFAULT_EXPRESSION_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid Snowflake default value: " + value);
        }
        return trimmed;
    }

    /**
     * Validate an index sort direction: only ASC/DESC are legal, returned in canonical uppercase.
     */
    public static String requireAscOrDesc(String value) {
        String trimmed = StringUtils.trimToEmpty(value);
        if ("ASC".equalsIgnoreCase(trimmed)) {
            return "ASC";
        }
        if ("DESC".equalsIgnoreCase(trimmed)) {
            return "DESC";
        }
        throw new IllegalArgumentException("Invalid Snowflake index sort direction: " + value);
    }
}
