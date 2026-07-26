package ai.chat2db.plugin.snowflake.identifier;

import ai.chat2db.spi.DefaultSQLIdentifierProcessor;
import org.apache.commons.lang3.StringUtils;

/**
 * Snowflake dialect identifier processor: double-quoted identifiers with embedded-quote
 * doubling, and single-quote doubling for string literals. Shared stateless
 * instance available via {@link #INSTANCE} for call sites without MetaData access.
 * <p>
 * {@link #quoteIdentifier(String)} is the SPI-facing conditional variant: identifiers
 * that are already valid and non-reserved are returned unquoted. {@link #quoteIdentifierAlways(String)}
 * is the unconditional variant reserved for DDL-generation call sites that historically
 * always quoted.
 */
public class SnowflakeIdentifierProcessor extends DefaultSQLIdentifierProcessor {

    public static final SnowflakeIdentifierProcessor INSTANCE = new SnowflakeIdentifierProcessor();

    /**
     * Conditionally quotes: {@code null} stays {@code null}, blank is returned unchanged,
     * identifiers already valid for the dialect (and not reserved keywords) are returned
     * unquoted; anything else is wrapped via {@link #quoteIdentifierAlways(String)}.
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
     * SPI always-quote variant that preserves the original identifier case.
     */
    @Override
    public String quoteIdentifierIgnoreCase(String identifier) {
        return quoteIdentifierAlways(identifier);
    }

    /**
     * Unconditionally quotes with double quotes, stripping one surrounding quote pair and
     * doubling every embedded double quote. {@code null} stays {@code null}, blank is
     * returned unchanged. For DDL-generation call sites only.
     */
    public String quoteIdentifierAlways(String identifier) {
        if (StringUtils.isBlank(identifier)) {
            return identifier;
        }
        return "\"" + stripAndEscape(identifier) + "\"";
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
