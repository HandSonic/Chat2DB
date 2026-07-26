package ai.chat2db.plugin.sqlite.identifier;

import ai.chat2db.spi.DefaultSQLIdentifierProcessor;
import org.apache.commons.lang3.StringUtils;

/**
 * SQLite dialect identifier processor: double-quoted identifiers with embedded-quote
 * doubling, and single-quote doubling for string literals. Identifiers that are
 * already valid pass through unquoted. Shared stateless instance available via
 * {@link #INSTANCE} for call sites without MetaData access.
 */
public class SqliteIdentifierProcessor extends DefaultSQLIdentifierProcessor {

    public static final SqliteIdentifierProcessor INSTANCE = new SqliteIdentifierProcessor();

    /**
     * Valid identifiers pass through unchanged; anything else is wrapped in double
     * quotes with one surrounding quote pair stripped and every embedded double
     * quote doubled.
     */
    @Override
    public String quoteIdentifier(String identifier) {
        if (isValidIdentifier(identifier)) {
            return identifier;
        }
        return "\"" + escapeIdentifierContent(identifier) + "\"";
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
     * doubling every single quote. Returns an empty string for {@code null}.
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
     * double quote. Returns an empty string for {@code null}.
     */
    public static String escapeIdentifier(String identifier) {
        return escapeIdentifierContent(identifier);
    }
}
