package ai.chat2db.plugin.hive.identifier;

import ai.chat2db.spi.DefaultSQLIdentifierProcessor;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

/**
 * Hive dialect identifier processor. The SPI-facing {@link #quoteIdentifier(String)}
 * is conditional: identifiers that are already valid plain identifiers (and not
 * reserved keywords) are returned unquoted so completion/matching consumers keep
 * working; anything else is wrapped in backticks. DDL-generation call sites that
 * historically always quoted use {@link #quoteIdentifierAlways(String)} (or the
 * SPI always-quote variant {@link #quoteIdentifierIgnoreCase(String)}), which
 * strips one surrounding backtick pair and doubles every embedded backtick.
 * String literals are escaped by doubling backslashes then single quotes (Hive
 * treats backslash as an escape character). Shared stateless instance available
 * via {@link #INSTANCE} for call sites without MetaData access.
 */
public class HiveIdentifierProcessor extends DefaultSQLIdentifierProcessor {

    public static final HiveIdentifierProcessor INSTANCE = new HiveIdentifierProcessor();

    private static final Pattern BACKTICK_PATTERN = Pattern.compile("[`\"](.*?)[`\"]");

    /**
     * Conditional quoting for SPI/completion paths: null/blank pass through;
     * valid plain identifiers that are not reserved keywords are returned
     * unquoted; everything else is backtick-quoted like {@link #quoteIdentifierAlways}.
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
     * SPI always-quote variant (preserve case, always quote).
     */
    @Override
    public String quoteIdentifierIgnoreCase(String identifier) {
        return quoteIdentifierAlways(identifier);
    }

    /**
     * Unconditional backtick quoting for DDL-generation paths: strips one
     * surrounding backtick pair, then doubles every embedded backtick.
     * Null/blank input is returned unchanged.
     */
    public String quoteIdentifierAlways(String identifier) {
        if (StringUtils.isBlank(identifier)) {
            return identifier;
        }
        return "`" + escapeIdentifierContent(identifier) + "`";
    }

    /**
     * Escapes a value interpolated into a single-quoted SQL string literal by
     * doubling backslashes first, then single quotes (Hive treats backslash as
     * an escape character).
     */
    @Override
    public String escapeString(String str) {
        if (str == null) {
            return null;
        }
        return str.replace("\\", "\\\\").replace("'", "''");
    }

    @Override
    public String removeIdentifierQuote(String identifier) {
        if (StringUtils.isBlank(identifier)) {
            return identifier;
        }
        return removePattern(identifier, BACKTICK_PATTERN);
    }

    @Override
    public boolean isQuoteIdentifier(String identifier) {
        if (StringUtils.isBlank(identifier)) {
            return false;
        }
        if (identifier.startsWith("`") && identifier.endsWith("`")) {
            return true;
        }
        return identifier.startsWith("\"") && identifier.endsWith("\"");
    }

    private static String escapeIdentifierContent(String identifier) {
        if (identifier == null) {
            return "";
        }
        String stripped = identifier;
        if (stripped.length() >= 2 && stripped.startsWith("`") && stripped.endsWith("`")) {
            stripped = stripped.substring(1, stripped.length() - 1);
        }
        return StringUtils.replace(stripped, "`", "``");
    }

    /**
     * Escapes identifier content for a position already surrounded by backticks:
     * strips one surrounding backtick pair, then doubles every embedded backtick.
     */
    public static String escapeIdentifier(String identifier) {
        return escapeIdentifierContent(identifier);
    }
}
