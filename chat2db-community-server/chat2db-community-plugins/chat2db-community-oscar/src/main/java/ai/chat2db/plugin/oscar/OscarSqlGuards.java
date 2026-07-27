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
    private static final Pattern KEYWORD_DEFAULT_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_$]*$");

    private OscarSqlGuards() {
    }

    /**
     * Validates a raw DEFAULT expression emitted verbatim into DDL (positions where
     * quoting would change semantics). Accepts numeric literals, single-quoted string
     * literals (with '' escapes), plain keywords such as SYSDATE/CURRENT_TIMESTAMP,
     * and simple function calls such as sys_guid() or to_date('2024-01-01','YYYY-MM-DD').
     * Quoted-literal and function-call shapes are recognized with linear-time scanners
     * (no regex), so the check cannot be driven into regex backtracking.
     */
    public static String requireDefaultValueExpression(String defaultValue) {
        if (StringUtils.isBlank(defaultValue)) {
            throw new IllegalArgumentException("Invalid Oscar default value: " + defaultValue);
        }
        String value = defaultValue.trim();
        if (NUMERIC_DEFAULT_PATTERN.matcher(value).matches()
                || KEYWORD_DEFAULT_PATTERN.matcher(value).matches()
                || isQuotedStringLiteral(value)
                || isSimpleFunctionCall(value)) {
            return value;
        }
        throw new IllegalArgumentException("Invalid Oscar default value: " + defaultValue);
    }

    /**
     * True when {@code s} is exactly one single-quoted string literal with '' escapes.
     * Linear scan, no backtracking.
     */
    static boolean isQuotedStringLiteral(String s) {
        return s.length() >= 2 && s.charAt(0) == '\'' && quotedLiteralEnd(s, 0) == s.length();
    }

    /**
     * Returns the index just past the single-quoted literal that starts at
     * {@code start} (where {@code s.charAt(start) == '\''}), or -1 when the literal
     * is unterminated. Doubled quotes are consumed as escapes.
     */
    private static int quotedLiteralEnd(String s, int start) {
        int i = start + 1;
        int n = s.length();
        while (i < n) {
            if (s.charAt(i) == '\'') {
                if (i + 1 < n && s.charAt(i + 1) == '\'') {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return -1;
    }

    /**
     * True for {@code name(args)} where args contain only identifier characters,
     * digits, dots, whitespace, commas, and single-quoted string literals (no nested
     * parentheses). Linear scan.
     */
    static boolean isSimpleFunctionCall(String s) {
        int n = s.length();
        if (n == 0 || !(Character.isLetter(s.charAt(0)) || s.charAt(0) == '_')) {
            return false;
        }
        int i = 1;
        while (i < n && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_' || s.charAt(i) == '$')) {
            i++;
        }
        while (i < n && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        if (i >= n || s.charAt(i) != '(') {
            return false;
        }
        i++;
        while (i < n) {
            char c = s.charAt(i);
            if (c == ')') {
                return i == n - 1;
            }
            if (c == '\'') {
                int end = quotedLiteralEnd(s, i);
                if (end < 0) {
                    return false;
                }
                i = end;
            } else if (Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '.'
                    || c == ',' || Character.isWhitespace(c)) {
                i++;
            } else {
                return false;
            }
        }
        return false;
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
