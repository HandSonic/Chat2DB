package ai.chat2db.plugin.sundb;

import java.util.regex.Pattern;

/**
 * Validation helpers for non-escapable SQL positions in SUNDB DDL generation
 * (index sort direction, column default expressions, VARCHAR units).
 * Escaping itself lives in {@link ai.chat2db.plugin.sundb.identifier.SUNDBIdentifierProcessor}.
 */
public final class SUNDBSqlGuards {

    // Two alternatives, fully anchored:
    //  1. bare token [A-Za-z0-9_ .+-]+ with a (?!.*--) guard so no comment
    //     sequence can appear — covers CURRENT_TIMESTAMP, SYSDATE, USER,
    //     SEQ.NEXTVAL, -1, 1.5. No quotes, commas or parentheses, so the
    //     value cannot smuggle literals, inject column defs, or close the
    //     column definition early.
    //  2. optionally keyword-prefixed single-quoted literal
    //     (?:[A-Za-z]+ )?'(?:[^']|'')*' — covers string defaults like 'Y',
    //     'O''Brien', '' and typed date/time literals like DATE '2024-01-01'.
    //     Interior quotes must be doubled, which exactly mirrors SQL
    //     string-literal tokenization, so anything accepted is parsed by the
    //     DB as one inert string token; the keyword is letters-only and
    //     cannot break out either. Parenthesized calls (TO_DATE(...),
    //     SYS_GUID()) remain rejected: parens could close the column def.
    private static final Pattern DEFAULT_VALUE_PATTERN = Pattern.compile(
            "\\A(?:(?!.*--)[A-Za-z0-9_ .+-]+|(?:[A-Za-z]+ )?'(?:[^']|'')*')\\z");

    private SUNDBSqlGuards() {
    }

    /**
     * Validates an index sort direction: only ASC/DESC are legal, returned in
     * canonical uppercase. Anything else is rejected to block DDL injection.
     */
    public static String requireAscOrDesc(String value) {
        String trimmed = value == null ? "" : value.trim();
        if ("ASC".equalsIgnoreCase(trimmed)) {
            return "ASC";
        }
        if ("DESC".equalsIgnoreCase(trimmed)) {
            return "DESC";
        }
        throw new IllegalArgumentException("Invalid SUNDB index sort direction: " + value);
    }

    /**
     * Validates an unquoted or single-quoted column default expression against a
     * conservative allow-list. Returns the value unchanged when it matches;
     * throws otherwise (fail closed) so hostile input cannot reshape the DDL.
     */
    public static String requireDefaultValue(String defaultValue) {
        if (!DEFAULT_VALUE_PATTERN.matcher(defaultValue).matches()) {
            throw new IllegalArgumentException("Unsupported DEFAULT expression: " + defaultValue);
        }
        return defaultValue;
    }

    /**
     * Validates a VARCHAR size unit: only CHAR/BYTE are legal. Anything else is
     * rejected to block DDL injection.
     */
    public static String requireUnit(String unit) {
        if (!"CHAR".equalsIgnoreCase(unit) && !"BYTE".equalsIgnoreCase(unit)) {
            throw new IllegalArgumentException("Unsupported VARCHAR unit: " + unit);
        }
        return unit;
    }
}
