package ai.chat2db.plugin.xugudb;

import java.util.regex.Pattern;

import ai.chat2db.plugin.xugudb.identifier.XugudbIdentifierProcessor;

/**
 * Validation helpers for non-escapable SQL positions in XUGUDB DDL generation
 * (column default expressions and length units supplied through table metadata).
 * Escaping itself lives in {@link XugudbIdentifierProcessor}.
 * Quoted-literal and function-call shapes are recognized with linear-time scanners
 * (no regex), so the checks cannot be driven into regex backtracking.
 */
public final class XugudbSqlGuards {

    /**
     * Conservative allow-list for length units (e.g. {@code BYTE}, {@code CHAR}).
     */
    private static final Pattern UNIT_PATTERN = Pattern.compile("^[A-Za-z]+$");

    private XugudbSqlGuards() {
    }

    /**
     * Validates a column default expression before it is embedded into generated DDL.
     * Accepts negative/positive numeric literals, single-quoted string literals with
     * '' escapes, and identifiers or function calls whose arguments are drawn from a
     * safe character set. Anything else is rejected so a hostile default cannot break
     * out of the DDL statement.
     */
    public static String requireDefaultValue(String defaultValue) {
        String trimmed = defaultValue.trim();
        if (!isNumericLiteral(trimmed) && !isQuotedStringLiteral(trimmed) && !isIdentifierOrCall(trimmed)) {
            throw new IllegalArgumentException("Unsupported column default value: " + defaultValue);
        }
        return trimmed;
    }

    private static boolean isNumericLiteral(String s) {
        int n = s.length();
        int i = s.startsWith("-") ? 1 : 0;
        boolean intDigits = false;
        while (i < n && Character.isDigit(s.charAt(i))) {
            i++;
            intDigits = true;
        }
        if (!intDigits) {
            return false;
        }
        if (i == n) {
            return true;
        }
        if (s.charAt(i) != '.') {
            return false;
        }
        i++;
        int fracStart = i;
        while (i < n && Character.isDigit(s.charAt(i))) {
            i++;
        }
        return i == n && i > fracStart;
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
     * True for an identifier ({@code [A-Za-z_][A-Za-z0-9_]*}) optionally followed by
     * a parenthesized argument list whose characters are letters, digits, underscores,
     * spaces, commas, dots, '-', '+' or single-quoted string literals (no nested
     * parentheses). Linear scan.
     */
    static boolean isIdentifierOrCall(String s) {
        int n = s.length();
        if (n == 0 || !(Character.isLetter(s.charAt(0)) || s.charAt(0) == '_')) {
            return false;
        }
        int i = 1;
        while (i < n && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) {
            i++;
        }
        if (i == n) {
            return true;
        }
        if (s.charAt(i) != '(') {
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
            } else if (Character.isLetterOrDigit(c) || c == '_' || c == ' ' || c == ','
                    || c == '.' || c == '-' || c == '+') {
                i++;
            } else {
                return false;
            }
        }
        return false;
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
