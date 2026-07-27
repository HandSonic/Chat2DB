package ai.chat2db.plugin.h2.identifier;

import ai.chat2db.spi.DefaultSQLIdentifierProcessor;
import org.apache.commons.lang3.StringUtils;

/**
 * H2 dialect identifier processor: double-quoted identifiers with embedded-quote
 * doubling, and single-quote doubling for string literals. Shared stateless
 * instance available via {@link #INSTANCE} for call sites without MetaData access.
 */
public class H2IdentifierProcessor extends DefaultSQLIdentifierProcessor {

    public static final H2IdentifierProcessor INSTANCE = new H2IdentifierProcessor();

    /**
     * SPI-facing conditional quoting: null/blank pass through unchanged; valid plain
     * identifiers stay unquoted (completion and matching paths rely on this); anything
     * else is double-quoted with one surrounding pair stripped and embedded quotes doubled.
     */
    @Override
    public String quoteIdentifier(String identifier) {
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

    @Override
    public String quoteIdentifierIgnoreCase(String identifier) {
        return quoteIdentifierAlways(identifier);
    }

    /**
     * Unconditional quoting for DDL-generation call sites: null/blank pass through,
     * anything else is wrapped in double quotes with doubling.
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
