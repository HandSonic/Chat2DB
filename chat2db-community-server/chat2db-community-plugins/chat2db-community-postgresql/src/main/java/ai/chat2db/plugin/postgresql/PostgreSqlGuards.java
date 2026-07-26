package ai.chat2db.plugin.postgresql;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

/**
 * Validation helpers for non-escapable SQL positions in PostgreSQL DDL/DML generation
 * (strict name tokens, raw DEFAULT expressions, bit/hex literal content, enum options).
 * Escaping itself lives in
 * {@link ai.chat2db.plugin.postgresql.identifier.PostgreSQLIdentifierProcessor}.
 */
public final class PostgreSqlGuards {

    private static final Pattern PG_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");
    private static final Pattern BIT_LITERAL_PATTERN = Pattern.compile("^[01]*$");
    private static final Pattern HEX_LITERAL_PATTERN = Pattern.compile("^[0-9a-fA-F]*$");
    // Two alternatives, fully anchored (mirrors the sundb wave-1 fix):
    //  1. bare token [A-Za-z0-9_ .+-]+ with a (?!.*--) guard so no comment sequence can appear
    //     — covers 0, -1, 1.5, true, CURRENT_TIMESTAMP, now, SEQ.NEXTVAL. No quotes, commas or
    //     parentheses, so the value cannot smuggle literals, inject column defs, or close the
    //     column definition early.
    //  2. anchored single-quoted literal '(?:[^']|'')*' — covers legitimate string defaults like
    //     'Y', '0', 'O''Brien', '1970-01-01', '{}', ''. Doubling is the only quote escape, so the
    //     literal cannot terminate early; anything after the closing quote fails the \z anchor.
    private static final Pattern DEFAULT_VALUE_PATTERN = Pattern.compile(
            "\\A(?:(?!.*--)[A-Za-z0-9_ .+-]+|'(?:[^']|'')*')\\z");

    private PostgreSqlGuards() {
    }

    /**
     * Validate a strict PostgreSQL name token (index method / role / keyword-style positions where
     * escaping is impossible by design).
     */
    public static String requirePgName(String value, String what) {
        if (value == null || !PG_NAME_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid PostgreSQL " + what + ": " + value);
        }
        return value;
    }

    /**
     * Validate a raw DEFAULT expression (a position where quoting would change semantics). Accepts
     * bare tokens (numbers, booleans, paren-less keywords) and single-quoted string literals with
     * doubled-quote escaping; rejects anything that could reshape the surrounding DDL.
     */
    public static String requireDefaultExpression(String value) {
        if (value == null || !DEFAULT_VALUE_PATTERN.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException("Invalid PostgreSQL default value: " + value);
        }
        return value;
    }

    /**
     * Validate content of a B'...' bit literal.
     */
    public static String requireBitLiteral(String value) {
        if (value == null || !BIT_LITERAL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid PostgreSQL bit literal: " + value);
        }
        return value;
    }

    /**
     * Validate content of a \x... bytea hex literal.
     */
    public static String requireHexLiteral(String value) {
        if (value == null || !HEX_LITERAL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid PostgreSQL bytea hex literal: " + value);
        }
        return value;
    }

    /**
     * Validate an option that must be one of the given enum constants (e.g. view check option).
     * Returns the canonical enum name.
     */
    public static <E extends Enum<E>> String requireEnumConstant(String value, E[] constants, String what) {
        for (E constant : constants) {
            if (constant.name().equalsIgnoreCase(StringUtils.trimToEmpty(value))) {
                return constant.name();
            }
        }
        throw new IllegalArgumentException("Invalid PostgreSQL " + what + ": " + value);
    }
}
