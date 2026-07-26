package ai.chat2db.plugin.clickhouse;

import org.apache.commons.lang3.StringUtils;

public final class ClickHouseSqlEscapes {

    private ClickHouseSqlEscapes() {
    }

    /**
     * Escapes a value interpolated into a single-quoted ClickHouse string literal.
     * ClickHouse treats backslash as an escape character, so backslashes are
     * doubled before single quotes are doubled.
     */
    public static String escapeSqlLiteral(String value) {
        if (value == null) {
            return "";
        }
        return StringUtils.replace(StringUtils.replace(value, "\\", "\\\\"), "'", "''");
    }

    /**
     * Quotes an identifier with backticks, stripping any surrounding backticks
     * and doubling every embedded backtick.
     */
    public static String quoteIdentifier(String identifier) {
        if (identifier == null) {
            return "``";
        }
        String stripped = identifier;
        if (stripped.length() >= 2 && stripped.startsWith("`") && stripped.endsWith("`")) {
            stripped = stripped.substring(1, stripped.length() - 1);
        }
        return "`" + StringUtils.replace(stripped, "`", "``") + "`";
    }

    /**
     * Validates a column type expression that is emitted verbatim into DDL
     * (e.g. Int32, Decimal(10,2), Array(Nullable(String)), Enum8('a','b')).
     * Only letters/digits/underscore are allowed at the top level; spaces,
     * commas and single quotes are only allowed inside balanced parentheses.
     * Semicolons, dashes, double quotes and backticks are always rejected.
     */
    public static String requireColumnTypeExpression(String columnType) {
        if (StringUtils.isBlank(columnType)) {
            throw new IllegalArgumentException("Invalid ClickHouse column type: " + columnType);
        }
        int depth = 0;
        for (int i = 0; i < columnType.length(); i++) {
            char c = columnType.charAt(i);
            if (c == '(') {
                depth++;
                continue;
            }
            if (c == ')') {
                depth--;
                if (depth < 0) {
                    throw new IllegalArgumentException("Invalid ClickHouse column type: " + columnType);
                }
                continue;
            }
            boolean ok = Character.isLetterOrDigit(c) || c == '_'
                    || (depth > 0 && (c == ' ' || c == ',' || c == '\''));
            if (!ok) {
                throw new IllegalArgumentException("Invalid ClickHouse column type: " + columnType);
            }
        }
        if (depth != 0 || !Character.isLetter(columnType.charAt(0))) {
            throw new IllegalArgumentException("Invalid ClickHouse column type: " + columnType);
        }
        return columnType;
    }
}
