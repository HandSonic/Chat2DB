package ai.chat2db.plugin.snowflake.identifier;

import ai.chat2db.spi.DefaultSQLIdentifierProcessor;
import org.apache.commons.lang3.StringUtils;

/**
 * Snowflake dialect identifier processor: double-quoted identifiers with embedded-quote
 * doubling, and single-quote doubling for string literals. Shared stateless
 * instance available via {@link #INSTANCE} for call sites without MetaData access.
 */
public class SnowflakeIdentifierProcessor extends DefaultSQLIdentifierProcessor {

    public static final SnowflakeIdentifierProcessor INSTANCE = new SnowflakeIdentifierProcessor();

    /**
     * Always quotes with double quotes, stripping one surrounding quote pair and
     * doubling every embedded double quote. Blank input is returned unchanged.
     */
    @Override
    public String quoteIdentifier(String identifier) {
        if (StringUtils.isBlank(identifier)) {
            return identifier;
        }
        return "\"" + stripAndEscape(identifier) + "\"";
    }

    @Override
    public String quoteIdentifier(String identifier, Integer majorVersion, Integer minorVersion) {
        return quoteIdentifier(identifier);
    }

    @Override
    public String quoteIdentifierIgnoreCase(String identifier) {
        return quoteIdentifier(identifier);
    }

    /**
     * Escapes a value interpolated into a single-quoted SQL string literal by
     * doubling every single quote.
     */
    @Override
    public String escapeString(String str) {
        return str == null ? null : StringUtils.replace(str, "'", "''");
    }

    private static String stripAndEscape(String identifier) {
        String stripped = identifier;
        if (stripped.length() >= 2 && stripped.startsWith("\"") && stripped.endsWith("\"")) {
            stripped = stripped.substring(1, stripped.length() - 1);
        }
        return StringUtils.replace(stripped, "\"", "\"\"");
    }

    /**
     * Escapes identifier content for a position already surrounded by double
     * quotes: every embedded double quote is doubled.
     */
    public static String escapeIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        return StringUtils.replace(identifier, "\"", "\"\"");
    }
}
