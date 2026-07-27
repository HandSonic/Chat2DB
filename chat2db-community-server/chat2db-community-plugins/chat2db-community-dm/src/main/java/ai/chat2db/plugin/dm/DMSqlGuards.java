package ai.chat2db.plugin.dm;

/**
 * Validation helpers for non-escapable SQL positions in DM DDL generation
 * (column DEFAULT expressions emitted verbatim). Escaping itself lives in
 * {@link ai.chat2db.plugin.dm.identifier.DMIdentifierProcessor}.
 * All shapes are recognized with linear-time scanners (no regex), so the
 * checks cannot be driven into regex backtracking.
 */
public final class DMSqlGuards {

    private DMSqlGuards() {
    }

    /**
     * Validates a column DEFAULT expression that is emitted verbatim into DDL.
     * Legitimate forms: quoted string literals (with '' escapes), numeric literals,
     * or keyword/function forms such as CURRENT_TIMESTAMP, SYSDATE, USER, SEQ.NEXTVAL.
     * Function-call arguments tolerate quoted string literals and nested balanced
     * parentheses (e.g. NVL(SUM(x),0)); semicolons are never allowed. Anything else
     * is rejected because DEFAULT values are emitted verbatim.
     */
    public static String requireDefaultExpression(String defaultValue) {
        String trimmed = defaultValue.trim();
        if (!isQuotedStringLiteral(trimmed) && !isNumericLiteral(trimmed) && !isIdentChainOrCall(trimmed)) {
            throw new IllegalArgumentException("Invalid DM default expression: " + defaultValue);
        }
        return trimmed;
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
     * Numeric literal: optional sign, digits with optional fractional part, or a
     * leading-dot fraction (e.g. {@code -1}, {@code 1.5}, {@code .5}).
     */
    private static boolean isNumericLiteral(String s) {
        int n = s.length();
        int i = (s.startsWith("+") || s.startsWith("-")) ? 1 : 0;
        boolean intDigits = false;
        while (i < n && Character.isDigit(s.charAt(i))) {
            i++;
            intDigits = true;
        }
        boolean dotSeen = false;
        boolean fracDigits = false;
        if (i < n && s.charAt(i) == '.') {
            dotSeen = true;
            i++;
            while (i < n && Character.isDigit(s.charAt(i))) {
                i++;
                fracDigits = true;
            }
        }
        if (i != n) {
            return false;
        }
        return dotSeen ? (intDigits || fracDigits) && fracDigits : intDigits;
    }

    /**
     * True for an identifier chain {@code ident(.ident)*} (e.g. SYSDATE, SEQ.NEXTVAL)
     * optionally followed by a parenthesized argument list. Arguments may contain
     * single-quoted string literals and nested balanced parentheses; semicolons and
     * stray quotes/parens are rejected. Linear scan.
     */
    static boolean isIdentChainOrCall(String s) {
        int n = s.length();
        if (n == 0 || !isIdentStart(s.charAt(0))) {
            return false;
        }
        int i = 1;
        while (i < n) {
            char c = s.charAt(i);
            if (isIdentPart(c)) {
                i++;
            } else if (c == '.' && i + 1 < n && isIdentStart(s.charAt(i + 1))) {
                i++;
            } else {
                break;
            }
        }
        while (i < n && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        if (i == n) {
            return true;
        }
        if (s.charAt(i) != '(') {
            return false;
        }
        int depth = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\'') {
                int end = quotedLiteralEnd(s, i);
                if (end < 0) {
                    return false;
                }
                i = end;
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i == n - 1;
                }
                if (depth < 0) {
                    return false;
                }
            } else if (c == ';') {
                return false;
            }
            i++;
        }
        return false;
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
