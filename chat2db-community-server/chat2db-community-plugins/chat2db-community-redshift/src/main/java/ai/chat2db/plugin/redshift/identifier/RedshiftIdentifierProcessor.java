package ai.chat2db.plugin.redshift.identifier;

import ai.chat2db.spi.DefaultSQLIdentifierProcessor;
import org.apache.commons.lang3.StringUtils;

/**
 * Redshift dialect identifier processor: double-quoted identifiers with embedded-quote
 * doubling, and single-quote doubling for string literals. Shared stateless
 * instance available via {@link #INSTANCE} for call sites without MetaData access.
 *
 * <p>{@link #quoteIdentifier(String)} follows the SPI contract: it is conditional
 * and only quotes when the identifier is not already a valid plain identifier
 * (or is a reserved keyword). {@link #quoteIdentifierAlways(String)} is the
 * unconditional variant reserved for DDL-generation call sites that historically
 * always produced quoted output.
 */
public class RedshiftIdentifierProcessor extends DefaultSQLIdentifierProcessor {

    public static final RedshiftIdentifierProcessor INSTANCE = new RedshiftIdentifierProcessor();

    /**
     * SPI-facing conditional quoting: returns {@code null} for {@code null},
     * blank input unchanged, a valid plain identifier that is not a reserved
     * keyword unquoted, and otherwise wraps with double quotes after stripping
     * one surrounding quote pair and doubling every embedded double quote.
     */
    @Override
    public String quoteIdentifier(String identifier) {
        if (StringUtils.isBlank(identifier)) {
            return identifier;
        }
        if (isValidIdentifier(identifier) && !isReservedKeyword(identifier.toUpperCase(), null, null)) {
            return identifier;
        }
        return quoteIdentifierAlways(identifier);
    }

    @Override
    public String quoteIdentifier(String identifier, Integer majorVersion, Integer minorVersion) {
        return quoteIdentifier(identifier);
    }

    /**
     * SPI "preserve case, always quote" variant: unconditionally wraps with
     * double quotes (blank input returned unchanged).
     */
    @Override
    public String quoteIdentifierIgnoreCase(String identifier) {
        if (StringUtils.isBlank(identifier)) {
            return identifier;
        }
        return quoteIdentifierAlways(identifier);
    }

    /**
     * Unconditional quoting for DDL-generation call sites: {@code null}/blank pass
     * through unchanged, otherwise wraps with double quotes after stripping one
     * surrounding quote pair and doubling every embedded double quote.
     */
    public String quoteIdentifierAlways(String identifier) {
        if (StringUtils.isBlank(identifier)) {
            return identifier;
        }
        return "\"" + escapeIdentifierContent(identifier) + "\"";
    }

    /**
     * Escapes a value interpolated into a single-quoted SQL string literal by
     * doubling every single quote.
     */
    @Override
    public String escapeString(String str) {
        return str == null ? null : StringUtils.replace(str, "'", "''");
    }

    private static String escapeIdentifierContent(String identifier) {
        String stripped = identifier;
        if (stripped.length() >= 2 && stripped.startsWith("\"") && stripped.endsWith("\"")) {
            stripped = stripped.substring(1, stripped.length() - 1);
        }
        return StringUtils.replace(stripped, "\"", "\"\"");
    }

    /**
     * Escapes identifier content for a position already surrounded by double
     * quotes: strips one surrounding quote pair, then doubles every embedded
     * double quote.
     */
    public static String escapeIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        return escapeIdentifierContent(identifier);
    }
}
