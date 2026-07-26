package ai.chat2db.plugin.kingbase.identifier;

import ai.chat2db.spi.DefaultSQLIdentifierProcessor;
import org.apache.commons.lang3.StringUtils;

/**
 * KingBase dialect identifier processor: double-quoted identifiers with embedded-quote
 * doubling, and single-quote doubling for string literals. Shared stateless
 * instance available via {@link #INSTANCE} for call sites without MetaData access.
 */
public class KingBaseSQLIdentifierProcessor extends DefaultSQLIdentifierProcessor {

    public static final KingBaseSQLIdentifierProcessor INSTANCE = new KingBaseSQLIdentifierProcessor();

    /**
     * SPI-facing conditional quoting: returns {@code null} for {@code null}, returns blank
     * input unchanged, returns a valid plain identifier (that is not a reserved keyword)
     * unquoted, and otherwise wraps with double quotes using
     * {@link #quoteIdentifierAlways(String)} semantics.
     */
    @Override
    public String quoteIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        if (StringUtils.isBlank(identifier)) {
            return identifier;
        }
        if (isValidIdentifier(identifier) && !isReservedKeyword(identifier, null, null)) {
            return identifier;
        }
        return quoteIdentifierAlways(identifier);
    }

    @Override
    public String quoteIdentifier(String identifier, Integer majorVersion, Integer minorVersion) {
        return quoteIdentifier(identifier);
    }

    /**
     * Always-quote SPI variant that preserves the original identifier case.
     */
    @Override
    public String quoteIdentifierIgnoreCase(String identifier) {
        return quoteIdentifierAlways(identifier);
    }

    /**
     * Unconditional quoting for DDL-generation call sites: returns {@code null} for
     * {@code null}, otherwise wraps with double quotes, stripping one surrounding quote
     * pair and doubling every embedded double quote.
     */
    public String quoteIdentifierAlways(String identifier) {
        if (identifier == null) {
            return null;
        }
        return "\"" + escapeIdentifierContent(identifier) + "\"";
    }

    /**
     * Escapes a value interpolated into a single-quoted SQL string literal by
     * doubling every single quote.
     */
    @Override
    public String escapeString(String str) {
        return str == null ? "" : StringUtils.replace(str, "'", "''");
    }

    private static String escapeIdentifierContent(String identifier) {
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
     * Escapes identifier content for a position already surrounded by double
     * quotes: strips one surrounding quote pair, then doubles every embedded
     * double quote.
     */
    public static String escapeIdentifier(String identifier) {
        return escapeIdentifierContent(identifier);
    }

}
