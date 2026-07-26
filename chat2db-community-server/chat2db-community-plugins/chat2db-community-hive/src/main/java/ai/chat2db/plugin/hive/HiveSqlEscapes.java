package ai.chat2db.plugin.hive;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

/**
 * Canonical escaping/quoting helpers for values interpolated into Hive SQL text (#1914).
 */
public final class HiveSqlEscapes {

    private static final Pattern HIVE_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");
    private static final Pattern NUMERIC_DEFAULT_PATTERN = Pattern.compile(
            "^([+-]?(\\d+(\\.\\d+)?|\\.\\d+)([eE][+-]?\\d+)?|0[xX][0-9a-fA-F]+|(?i:TRUE|FALSE))$");

    private HiveSqlEscapes() {
    }

    /**
     * Escape a value interpolated into a single-quoted SQL string literal (surrounding quotes NOT added).
     * Hive treats backslash as an escape character, so backslashes are doubled before single quotes.
     */
    public static String escapeSqlLiteral(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\\", "\\\\").replace("'", "''");
    }

    /**
     * Quote an identifier with backticks: strips one surrounding backtick pair, then doubles every
     * embedded backtick.
     */
    public static String quoteIdentifier(String name) {
        if (StringUtils.isBlank(name)) {
            return name;
        }
        String identifier = name;
        if (identifier.length() >= 2 && identifier.startsWith("`") && identifier.endsWith("`")) {
            identifier = identifier.substring(1, identifier.length() - 1);
        }
        return "`" + identifier.replace("`", "``") + "`";
    }

    /**
     * Validate a strict Hive name token (ENGINE / CHARACTER SET / COLLATE style positions where
     * escaping is impossible by design).
     */
    public static String requireHiveName(String value, String what) {
        if (value == null || !HIVE_NAME_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid Hive " + what + ": " + value);
        }
        return value;
    }

    /**
     * Validate a raw DEFAULT literal for numeric-ish columns (positions where quoting would change
     * semantics). Accepts decimal/scientific numbers, hex literals, TRUE/FALSE.
     */
    public static String requireNumericDefault(String value) {
        if (value == null || !NUMERIC_DEFAULT_PATTERN.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException("Invalid Hive default value: " + value);
        }
        return value;
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
        throw new IllegalArgumentException("Invalid Hive index sort direction: " + value);
    }
}
