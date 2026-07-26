package ai.chat2db.plugin.oracle;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

/**
 * Validation helpers for non-escapable SQL positions in Oracle DDL/DML generation
 * (column DEFAULT expressions, column type names, VARCHAR units, index sort
 * directions and RAW/BLOB hex literals). Escaping itself lives in
 * {@link ai.chat2db.plugin.oracle.identifier.OracleIdentifierProcessor}.
 */
public final class OracleSqlGuards {

    /**
     * Two alternatives, fully anchored:
     *  1. bare token [A-Za-z0-9_ .+-]+ with a (?!.*--) guard so no comment sequence can
     *     appear — covers CURRENT_TIMESTAMP, SYSDATE, USER, SEQ.NEXTVAL, -1, 1.5. No quotes,
     *     commas or parentheses, so the value cannot smuggle literals, inject column defs,
     *     or close the column definition early.
     *  2. anchored single-quoted literal '(?:[^']|'')*' — covers legitimate string defaults
     *     like 'Y', '0', 'O''Brien', '1970-01-01', ''. Doubling is the only quote escape, so
     *     the literal cannot terminate early; anything after the closing quote fails the \z anchor.
     */
    private static final Pattern DEFAULT_VALUE_PATTERN = Pattern.compile(
            "\\A(?:(?!.*--)[A-Za-z0-9_ .+-]+|'(?:[^']|'')*')\\z");

    /**
     * Conservative allow-list for a column type string used in the unknown-type fallback:
     * word characters and spaces, plus parenthesized groups of word characters/digits/commas,
     * so VARCHAR2(20), NUMBER(10,2) and TIMESTAMP(6) WITH TIME ZONE keep working while
     * quotes, semicolons, comment sequences and stray commas/parens are rejected.
     * Characters legal in Oracle supplied/user-defined type names (#, $, +, ., /) are also allowed.
     */
    private static final Pattern SAFE_TYPE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_ #$+./]*(\\([A-Za-z0-9_ ,]+\\)[A-Za-z0-9_ ]*)*");

    private OracleSqlGuards() {
    }

    /**
     * Validate a raw DEFAULT expression (a position where quoting would change semantics).
     * Accepts bare tokens (SYSDATE, SEQ.NEXTVAL, -1, 1.5) and well-formed single-quoted
     * string literals; rejects anything that could reshape the DDL.
     */
    public static String requireDefaultValue(String value) {
        if (value == null || !DEFAULT_VALUE_PATTERN.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException("Unsupported DEFAULT expression: " + value);
        }
        return value;
    }

    /**
     * Validate a column type string for the unknown-type fallback (a position where
     * escaping is impossible by design).
     */
    public static String requireSafeTypeName(String typeName) {
        if (typeName == null || !SAFE_TYPE_NAME.matcher(typeName.trim()).matches()) {
            throw new IllegalArgumentException("Unsafe column type name: " + typeName);
        }
        return typeName;
    }

    /**
     * Validate a VARCHAR length unit: only CHAR/BYTE are legal.
     */
    public static String requireUnit(String unit) {
        if (!"CHAR".equalsIgnoreCase(unit) && !"BYTE".equalsIgnoreCase(unit)) {
            throw new IllegalArgumentException("Unsupported VARCHAR unit: " + unit);
        }
        return unit;
    }

    /**
     * Validate an index sort direction: only ASC/DESC are legal, returned in canonical
     * uppercase. Anything else is rejected to block DDL injection.
     */
    public static String requireAscOrDesc(String value) {
        String trimmed = StringUtils.trimToEmpty(value);
        if ("ASC".equalsIgnoreCase(trimmed)) {
            return "ASC";
        }
        if ("DESC".equalsIgnoreCase(trimmed)) {
            return "DESC";
        }
        throw new IllegalArgumentException("Invalid Oracle index sort direction: " + value);
    }

    /**
     * Validate a RAW/BLOB hex literal (unquoted hex digits only). Returns the value when
     * every character is a hex digit, otherwise the provided fallback (callers pass a
     * base16 encoding, which is safe by construction).
     */
    public static String hexLiteralOrFallback(String value, String fallbackHex) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean isDigit = (c >= '0' && c <= '9');
            boolean isUpperCaseHex = (c >= 'A' && c <= 'F');
            boolean isLowerCaseHex = (c >= 'a' && c <= 'f');
            if (!isDigit && !isUpperCaseHex && !isLowerCaseHex) {
                return fallbackHex;
            }
        }
        return value;
    }
}
