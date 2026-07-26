package ai.chat2db.plugin.clickhouse.identifier;

import ai.chat2db.spi.DefaultSQLIdentifierProcessor;
import org.apache.commons.lang3.StringUtils;

/**
 * ClickHouse dialect identifier processor: backtick-quoted identifiers with
 * embedded-backtick doubling, and backslash/single-quote doubling for string
 * literals (ClickHouse treats backslash as an escape character). Shared
 * stateless instance available via {@link #INSTANCE} for call sites without
 * MetaData access.
 */
public class ClickHouseIdentifierProcessor extends DefaultSQLIdentifierProcessor {

    public static final ClickHouseIdentifierProcessor INSTANCE = new ClickHouseIdentifierProcessor();

    /**
     * Always quotes with backticks, stripping one surrounding backtick pair and
     * doubling every embedded backtick.
     */
    @Override
    public String quoteIdentifier(String identifier) {
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
     * Escapes a value interpolated into a single-quoted ClickHouse string
     * literal: backslashes are doubled first (ClickHouse treats backslash as an
     * escape character), then single quotes are doubled.
     */
    @Override
    public String escapeString(String str) {
        return str == null ? "" : StringUtils.replace(StringUtils.replace(str, "\\", "\\\\"), "'", "''");
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
     * Escapes identifier content for a position already surrounded by
     * backticks: strips one surrounding backtick pair, then doubles every
     * embedded backtick.
     */
    public static String escapeIdentifier(String identifier) {
        return escapeIdentifierContent(identifier);
    }
}
