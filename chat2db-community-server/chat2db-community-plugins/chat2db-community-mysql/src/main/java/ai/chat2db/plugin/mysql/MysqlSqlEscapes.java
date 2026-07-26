package ai.chat2db.plugin.mysql;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Canonical escaping/quoting helpers for values interpolated into MySQL SQL text (#1914).
 * Literal escaping mirrors MysqlAccountSqlBuilder.stringLiteral (backslash first, then single quotes).
 */
public final class MysqlSqlEscapes {

    private static final Pattern MYSQL_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");
    private static final Pattern NUMERIC_DEFAULT_PATTERN = Pattern.compile(
            "^([+-]?(\\d+(\\.\\d+)?|\\.\\d+)([eE][+-]?\\d+)?|0[xX][0-9a-fA-F]+|[xX]'[0-9a-fA-F]*'|[bB]'[01]*'|(?i:TRUE|FALSE))$");
    private static final Pattern BIT_LITERAL_PATTERN = Pattern.compile("^[01]+$");
    private static final Pattern HEX_DIGITS_PATTERN = Pattern.compile("^[0-9a-fA-F]+$");
    private static final Pattern HEX_LITERAL_PATTERN = Pattern.compile("^0[xX][0-9a-fA-F]+$");
    private static final String DEFINER_QUOTED_PART = "'([^'\\\\]|\\\\[\\s\\S])*'|`[^`]+`";
    private static final Pattern DEFINER_PATTERN = Pattern.compile(
            "^([A-Za-z0-9_$]+|" + DEFINER_QUOTED_PART + ")@([A-Za-z0-9_.%:$-]+|" + DEFINER_QUOTED_PART + ")$");

    private MysqlSqlEscapes() {
    }

    /**
     * Escape a value interpolated into a single-quoted SQL string literal (surrounding quotes NOT added).
     * MySQL treats backslash as an escape character, so backslashes are doubled before single quotes.
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
     * Validate a strict MySQL name token (ENGINE / CHARACTER SET / COLLATE style positions where
     * escaping is impossible by design).
     */
    public static String requireMysqlName(String value, String what) {
        if (value == null || !MYSQL_NAME_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid MySQL " + what + ": " + value);
        }
        return value;
    }

    /**
     * Validate a raw DEFAULT literal for numeric-ish columns (positions where quoting would change
     * semantics). Accepts decimal/scientific numbers, hex and bit literals, TRUE/FALSE.
     */
    public static String requireNumericDefault(String value) {
        if (value == null || !NUMERIC_DEFAULT_PATTERN.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException("Invalid MySQL default value: " + value);
        }
        return value;
    }

    /**
     * Validate content of a b'...' bit literal.
     */
    public static String requireBitLiteral(String value) {
        if (value == null || !BIT_LITERAL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid MySQL bit literal: " + value);
        }
        return value;
    }

    /**
     * Validate the digits of a 0x... hex literal (the template adds the 0x prefix).
     */
    public static String requireHexDigits(String value) {
        if (value == null || !HEX_DIGITS_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid MySQL hex digits: " + value);
        }
        return value;
    }

    /**
     * True only when the value is a well-formed 0x... hex literal. Values that merely
     * start with 0x but contain non-hex characters must not pass through into SQL raw.
     */
    public static boolean isHexLiteral(String value) {
        return value != null && HEX_LITERAL_PATTERN.matcher(value).matches();
    }

    /**
     * Quote an identifier with backticks without stripping a surrounding pair: every
     * embedded backtick is doubled. For call sites where the name may itself start or
     * end with a backtick character.
     */
    public static String quoteIdentifierRaw(String name) {
        if (StringUtils.isBlank(name)) {
            return name;
        }
        return "`" + name.replace("`", "``") + "`";
    }

    /**
     * Validate a DEFINER value (user@host, parts optionally single-quoted or backtick-quoted).
     */
    public static String requireDefiner(String value) {
        if (value == null || !DEFINER_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid MySQL definer: " + value);
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
        throw new IllegalArgumentException("Invalid MySQL index sort direction: " + value);
    }

    /**
     * Validate an option that must be one of the given enum constants (e.g. view algorithm /
     * sql security / check option). Returns the canonical enum name.
     */
    public static <E extends Enum<E>> String requireEnumConstant(String value, E[] constants, String what) {
        for (E constant : constants) {
            if (constant.name().equalsIgnoreCase(StringUtils.trimToEmpty(value))) {
                return constant.name();
            }
        }
        throw new IllegalArgumentException("Invalid MySQL " + what + ": " + value);
    }

    /**
     * Re-escape a comma-separated ENUM/SET value list: each item is stripped of its surrounding
     * single quotes (if any), escaped as a SQL string literal and re-quoted. A surrounding pair of
     * parentheses is preserved.
     */
    public static String quoteEnumValues(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        boolean parenthesized = trimmed.length() >= 2 && trimmed.startsWith("(") && trimmed.endsWith(")");
        String inner = parenthesized ? trimmed.substring(1, trimmed.length() - 1) : trimmed;
        List<String> items = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (inQuote) {
                current.append(c);
                if (c == '\\' && i + 1 < inner.length()) {
                    current.append(inner.charAt(++i));
                } else if (c == '\'') {
                    if (i + 1 < inner.length() && inner.charAt(i + 1) == '\'') {
                        current.append('\'');
                        i++;
                    } else {
                        inQuote = false;
                    }
                }
            } else if (c == '\'') {
                inQuote = true;
                current.append(c);
            } else if (c == ',') {
                items.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        items.add(current.toString());
        StringBuilder result = new StringBuilder();
        if (parenthesized) {
            result.append('(');
        }
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                result.append(',');
            }
            result.append('\'').append(escapeSqlLiteral(unquoteSingle(items.get(i).trim()))).append('\'');
        }
        if (parenthesized) {
            result.append(')');
        }
        return result.toString();
    }

    private static String unquoteSingle(String item) {
        if (item.length() >= 2 && item.startsWith("'") && item.endsWith("'")) {
            return item.substring(1, item.length() - 1);
        }
        return item;
    }
}
