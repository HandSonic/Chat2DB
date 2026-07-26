package ai.chat2db.plugin.h2;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * Escaping helpers for H2 SQL generation.
 *
 * <p>Contract: callers MUST pass raw (unquoted, unescaped) names and values.
 * {@link #escapeIdentifier(String)} tolerates at most one surrounding pair of double quotes
 * for convenience, but passing an already-escaped identifier (e.g. {@code we""ird}) will
 * double-escape it and produce a wrong name. Never feed output of these helpers back in.</p>
 */
public final class H2SqlEscapes {

    /**
     * Conservative allow-list for column type names reported by JDBC metadata
     * (e.g. {@code INTEGER}, {@code CHARACTER VARYING}). Anything else is rejected
     * so hostile or corrupt metadata cannot smuggle SQL into generated DDL.
     */
    private static final Pattern SAFE_TYPE_NAME = Pattern.compile("[A-Za-z0-9_() ]+");

    /**
     * Conservative allow-list for unquoted column default expressions
     * (e.g. {@code 42}, {@code -1}, {@code CURRENT_TIMESTAMP}, {@code NEXT VALUE FOR S}).
     * Excludes quotes and semicolons so an expression cannot break out of the DDL statement.
     */
    private static final Pattern SAFE_DEFAULT_EXPRESSION = Pattern.compile("[A-Za-z0-9_()., +\\-*/%]+");

    private H2SqlEscapes() {
    }

    /**
     * Escapes a raw string literal value for inclusion inside single quotes
     * (single quotes are doubled, per the SQL standard which H2 follows).
     * Returns an empty string for {@code null}.
     */
    public static String escapeSqlLiteral(String value) {
        return value == null ? "" : StringUtils.replace(value, "'", "''");
    }

    /**
     * Escapes a raw identifier for inclusion inside double quotes
     * (embedded double quotes are doubled). One surrounding pair of double quotes
     * is stripped first; do NOT pass already-escaped identifiers. Returns an empty
     * string for {@code null}.
     */
    public static String escapeIdentifier(String identifier) {
        if (identifier == null) {
            return "";
        }
        String stripped = identifier;
        if (stripped.length() >= 2 && stripped.startsWith("\"") && stripped.endsWith("\"")) {
            stripped = stripped.substring(1, stripped.length() - 1);
        }
        return StringUtils.replace(stripped, "\"", "\"\"");
    }

    /**
     * Returns the raw identifier wrapped in double quotes with embedded quotes doubled.
     */
    public static String quoteIdentifier(String identifier) {
        return "\"" + escapeIdentifier(identifier) + "\"";
    }

    /**
     * Validates a column type name obtained from JDBC metadata before it is embedded
     * into generated DDL. Returns the type name unchanged when it matches a conservative
     * allow-list; throws otherwise (fail closed).
     *
     * @throws IllegalArgumentException if the type name contains unexpected characters
     */
    public static String requireSafeTypeName(String typeName) {
        if (typeName != null && !SAFE_TYPE_NAME.matcher(typeName).matches()) {
            throw new IllegalArgumentException("Unsafe column type name from metadata: " + typeName);
        }
        return typeName;
    }

    /**
     * Renders a column default expression obtained from JDBC metadata (COLUMN_DEF) safe for
     * inclusion in generated DDL:
     * <ul>
     *   <li>values wrapped in single quotes are treated as string literals: if the inner
     *   content is well-formed (quotes correctly doubled) it is passed through unchanged,
     *   otherwise the inner content is re-escaped;</li>
     *   <li>unquoted values matching a conservative expression allow-list (numbers,
     *   keywords, function calls) are passed through unchanged;</li>
     *   <li>anything else is neutralised by rendering it as an escaped string literal.</li>
     * </ul>
     * Returns an empty string for {@code null}.
     */
    public static String escapeColumnDefault(String columnDefault) {
        if (columnDefault == null) {
            return "";
        }
        String trimmed = columnDefault.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("'") && trimmed.endsWith("'")) {
            String inner = trimmed.substring(1, trimmed.length() - 1);
            if (isWellFormedEscapedLiteral(inner)) {
                return trimmed;
            }
            return "'" + escapeSqlLiteral(inner) + "'";
        }
        if (SAFE_DEFAULT_EXPRESSION.matcher(trimmed).matches()) {
            return trimmed;
        }
        return "'" + escapeSqlLiteral(trimmed) + "'";
    }

    /**
     * Checks that every single quote in the inner content of a quoted literal is
     * correctly doubled (the only escape mechanism H2 uses inside string literals).
     */
    private static boolean isWellFormedEscapedLiteral(String inner) {
        for (int i = 0; i < inner.length(); i++) {
            if (inner.charAt(i) == '\'') {
                if (i + 1 >= inner.length() || inner.charAt(i + 1) != '\'') {
                    return false;
                }
                i++;
            }
        }
        return true;
    }
}
