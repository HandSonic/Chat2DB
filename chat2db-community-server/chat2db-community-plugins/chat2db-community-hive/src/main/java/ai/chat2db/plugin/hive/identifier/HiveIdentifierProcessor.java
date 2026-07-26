package ai.chat2db.plugin.hive.identifier;

import ai.chat2db.spi.DefaultSQLIdentifierProcessor;
import org.apache.commons.lang3.StringUtils;

/**
 * Hive dialect identifier processor: backtick-quoted identifiers with
 * embedded-backtick doubling, and backslash-then-single-quote doubling for
 * string literals (Hive treats backslash as an escape character). Shared
 * stateless instance available via {@link #INSTANCE} for call sites without
 * MetaData access.
 */
public class HiveIdentifierProcessor extends DefaultSQLIdentifierProcessor {

    public static final HiveIdentifierProcessor INSTANCE = new HiveIdentifierProcessor();

    /**
     * Always quotes with backticks, stripping one surrounding backtick pair and
     * doubling every embedded backtick. Blank input is returned unchanged.
     */
    @Override
    public String quoteIdentifier(String identifier) {
        if (StringUtils.isBlank(identifier)) {
            return identifier;
        }
        return "`" + escapeIdentifierContent(identifier) + "`";
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
