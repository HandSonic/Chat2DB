package ai.chat2db.plugin.clickhouse.identifier;

import ai.chat2db.spi.DefaultSQLIdentifierProcessor;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * ClickHouse dialect identifier processor.
 *
 * <p>SPI-facing {@link #quoteIdentifier(String)} is conditional: plain
 * identifiers that are not reserved keywords pass through unquoted so
 * completion/matching consumers see raw names; anything else is wrapped in
 * backticks with embedded backticks doubled. {@link #quoteIdentifierAlways(String)}
 * is the unconditional variant reserved for DDL-generation call sites that
 * historically always quoted. Backslash/single-quote doubling is used for
 * string literals (ClickHouse treats backslash as an escape character).
 * Shared stateless instance available via {@link #INSTANCE} for call sites
 * without MetaData access.
 */
public class ClickHouseIdentifierProcessor extends DefaultSQLIdentifierProcessor {

    public static final ClickHouseIdentifierProcessor INSTANCE = new ClickHouseIdentifierProcessor();

    private static final Pattern CLICKHOUSE_PATTERN = Pattern.compile("[`\"](.*?)[`\"]");

    private static final Set<String> CLICKHOUSE_RESERVED_KEYWORDS = new HashSet<>();

    static {
        CLICKHOUSE_RESERVED_KEYWORDS.add("ALIAS");
        CLICKHOUSE_RESERVED_KEYWORDS.add("ALL");
        CLICKHOUSE_RESERVED_KEYWORDS.add("ALTER");
        CLICKHOUSE_RESERVED_KEYWORDS.add("AND");
        CLICKHOUSE_RESERVED_KEYWORDS.add("ANTI");
        CLICKHOUSE_RESERVED_KEYWORDS.add("ANY");
        CLICKHOUSE_RESERVED_KEYWORDS.add("ARRAY");
        CLICKHOUSE_RESERVED_KEYWORDS.add("AS");
        CLICKHOUSE_RESERVED_KEYWORDS.add("ASC");
        CLICKHOUSE_RESERVED_KEYWORDS.add("ASOF");
        CLICKHOUSE_RESERVED_KEYWORDS.add("BETWEEN");
        CLICKHOUSE_RESERVED_KEYWORDS.add("BOTH");
        CLICKHOUSE_RESERVED_KEYWORDS.add("BY");
        CLICKHOUSE_RESERVED_KEYWORDS.add("CASE");
        CLICKHOUSE_RESERVED_KEYWORDS.add("CAST");
        CLICKHOUSE_RESERVED_KEYWORDS.add("CHECK");
        CLICKHOUSE_RESERVED_KEYWORDS.add("CLUSTER");
        CLICKHOUSE_RESERVED_KEYWORDS.add("COMMENT");
        CLICKHOUSE_RESERVED_KEYWORDS.add("CONSTRAINT");
        CLICKHOUSE_RESERVED_KEYWORDS.add("CREATE");
        CLICKHOUSE_RESERVED_KEYWORDS.add("CROSS");
        CLICKHOUSE_RESERVED_KEYWORDS.add("CUBE");
        CLICKHOUSE_RESERVED_KEYWORDS.add("DATABASE");
        CLICKHOUSE_RESERVED_KEYWORDS.add("DATABASES");
        CLICKHOUSE_RESERVED_KEYWORDS.add("DEFAULT");
        CLICKHOUSE_RESERVED_KEYWORDS.add("DELETE");
        CLICKHOUSE_RESERVED_KEYWORDS.add("DESC");
        CLICKHOUSE_RESERVED_KEYWORDS.add("DESCRIBE");
        CLICKHOUSE_RESERVED_KEYWORDS.add("DETACH");
        CLICKHOUSE_RESERVED_KEYWORDS.add("DICTIONARY");
        CLICKHOUSE_RESERVED_KEYWORDS.add("DISTINCT");
        CLICKHOUSE_RESERVED_KEYWORDS.add("DISTRIBUTED");
        CLICKHOUSE_RESERVED_KEYWORDS.add("DROP");
        CLICKHOUSE_RESERVED_KEYWORDS.add("ELSE");
        CLICKHOUSE_RESERVED_KEYWORDS.add("END");
        CLICKHOUSE_RESERVED_KEYWORDS.add("ENGINE");
        CLICKHOUSE_RESERVED_KEYWORDS.add("EPHEMERAL");
        CLICKHOUSE_RESERVED_KEYWORDS.add("EXCEPT");
        CLICKHOUSE_RESERVED_KEYWORDS.add("EXISTS");
        CLICKHOUSE_RESERVED_KEYWORDS.add("EXPLAIN");
        CLICKHOUSE_RESERVED_KEYWORDS.add("FINAL");
        CLICKHOUSE_RESERVED_KEYWORDS.add("FIRST");
        CLICKHOUSE_RESERVED_KEYWORDS.add("FOLLOWING");
        CLICKHOUSE_RESERVED_KEYWORDS.add("FORMAT");
        CLICKHOUSE_RESERVED_KEYWORDS.add("FROM");
        CLICKHOUSE_RESERVED_KEYWORDS.add("FULL");
        CLICKHOUSE_RESERVED_KEYWORDS.add("FUNCTION");
        CLICKHOUSE_RESERVED_KEYWORDS.add("GLOBAL");
        CLICKHOUSE_RESERVED_KEYWORDS.add("GRANULARITY");
        CLICKHOUSE_RESERVED_KEYWORDS.add("GROUP");
        CLICKHOUSE_RESERVED_KEYWORDS.add("HAVING");
        CLICKHOUSE_RESERVED_KEYWORDS.add("IF");
        CLICKHOUSE_RESERVED_KEYWORDS.add("ILIKE");
        CLICKHOUSE_RESERVED_KEYWORDS.add("IN");
        CLICKHOUSE_RESERVED_KEYWORDS.add("INDEX");
        CLICKHOUSE_RESERVED_KEYWORDS.add("INNER");
        CLICKHOUSE_RESERVED_KEYWORDS.add("INSERT");
        CLICKHOUSE_RESERVED_KEYWORDS.add("INTERSECT");
        CLICKHOUSE_RESERVED_KEYWORDS.add("INTERVAL");
        CLICKHOUSE_RESERVED_KEYWORDS.add("INTO");
        CLICKHOUSE_RESERVED_KEYWORDS.add("IS");
        CLICKHOUSE_RESERVED_KEYWORDS.add("JOIN");
        CLICKHOUSE_RESERVED_KEYWORDS.add("KEY");
        CLICKHOUSE_RESERVED_KEYWORDS.add("LAST");
        CLICKHOUSE_RESERVED_KEYWORDS.add("LEADING");
        CLICKHOUSE_RESERVED_KEYWORDS.add("LEFT");
        CLICKHOUSE_RESERVED_KEYWORDS.add("LIKE");
        CLICKHOUSE_RESERVED_KEYWORDS.add("LIMIT");
        CLICKHOUSE_RESERVED_KEYWORDS.add("LIVE");
        CLICKHOUSE_RESERVED_KEYWORDS.add("LOCAL");
        CLICKHOUSE_RESERVED_KEYWORDS.add("MATERIALIZED");
        CLICKHOUSE_RESERVED_KEYWORDS.add("MODIFY");
        CLICKHOUSE_RESERVED_KEYWORDS.add("MUTATION");
        CLICKHOUSE_RESERVED_KEYWORDS.add("NOT");
        CLICKHOUSE_RESERVED_KEYWORDS.add("NULL");
        CLICKHOUSE_RESERVED_KEYWORDS.add("NULLS");
        CLICKHOUSE_RESERVED_KEYWORDS.add("OFFSET");
        CLICKHOUSE_RESERVED_KEYWORDS.add("ON");
        CLICKHOUSE_RESERVED_KEYWORDS.add("OPTIMIZE");
        CLICKHOUSE_RESERVED_KEYWORDS.add("OR");
        CLICKHOUSE_RESERVED_KEYWORDS.add("ORDER");
        CLICKHOUSE_RESERVED_KEYWORDS.add("OUTER");
        CLICKHOUSE_RESERVED_KEYWORDS.add("OVER");
        CLICKHOUSE_RESERVED_KEYWORDS.add("PARTITION");
        CLICKHOUSE_RESERVED_KEYWORDS.add("POPULATE");
        CLICKHOUSE_RESERVED_KEYWORDS.add("PRECEDING");
        CLICKHOUSE_RESERVED_KEYWORDS.add("PREWHERE");
        CLICKHOUSE_RESERVED_KEYWORDS.add("PRIMARY");
        CLICKHOUSE_RESERVED_KEYWORDS.add("PROJECTION");
        CLICKHOUSE_RESERVED_KEYWORDS.add("RANGE");
        CLICKHOUSE_RESERVED_KEYWORDS.add("RENAME");
        CLICKHOUSE_RESERVED_KEYWORDS.add("REPLACE");
        CLICKHOUSE_RESERVED_KEYWORDS.add("REPLICA");
        CLICKHOUSE_RESERVED_KEYWORDS.add("RIGHT");
        CLICKHOUSE_RESERVED_KEYWORDS.add("ROLLUP");
        CLICKHOUSE_RESERVED_KEYWORDS.add("SAMPLE");
        CLICKHOUSE_RESERVED_KEYWORDS.add("SELECT");
        CLICKHOUSE_RESERVED_KEYWORDS.add("SEMI");
        CLICKHOUSE_RESERVED_KEYWORDS.add("SET");
        CLICKHOUSE_RESERVED_KEYWORDS.add("SETTINGS");
        CLICKHOUSE_RESERVED_KEYWORDS.add("SHOW");
        CLICKHOUSE_RESERVED_KEYWORDS.add("TABLE");
        CLICKHOUSE_RESERVED_KEYWORDS.add("TABLES");
        CLICKHOUSE_RESERVED_KEYWORDS.add("TEMPORARY");
        CLICKHOUSE_RESERVED_KEYWORDS.add("THEN");
        CLICKHOUSE_RESERVED_KEYWORDS.add("TIES");
        CLICKHOUSE_RESERVED_KEYWORDS.add("TO");
        CLICKHOUSE_RESERVED_KEYWORDS.add("TOP");
        CLICKHOUSE_RESERVED_KEYWORDS.add("TOTALS");
        CLICKHOUSE_RESERVED_KEYWORDS.add("TRAILING");
        CLICKHOUSE_RESERVED_KEYWORDS.add("TRIM");
        CLICKHOUSE_RESERVED_KEYWORDS.add("TRUNCATE");
        CLICKHOUSE_RESERVED_KEYWORDS.add("TTL");
        CLICKHOUSE_RESERVED_KEYWORDS.add("UNBOUNDED");
        CLICKHOUSE_RESERVED_KEYWORDS.add("UNION");
        CLICKHOUSE_RESERVED_KEYWORDS.add("UPDATE");
        CLICKHOUSE_RESERVED_KEYWORDS.add("USE");
        CLICKHOUSE_RESERVED_KEYWORDS.add("USING");
        CLICKHOUSE_RESERVED_KEYWORDS.add("VALUES");
        CLICKHOUSE_RESERVED_KEYWORDS.add("VIEW");
        CLICKHOUSE_RESERVED_KEYWORDS.add("VOLUME");
        CLICKHOUSE_RESERVED_KEYWORDS.add("WATCH");
        CLICKHOUSE_RESERVED_KEYWORDS.add("WHEN");
        CLICKHOUSE_RESERVED_KEYWORDS.add("WHERE");
        CLICKHOUSE_RESERVED_KEYWORDS.add("WINDOW");
        CLICKHOUSE_RESERVED_KEYWORDS.add("WITH");
    }

    @Override
    public boolean isReservedKeyword(String identifier, Integer majorVersion, Integer minorVersion) {
        return identifier != null && CLICKHOUSE_RESERVED_KEYWORDS.contains(identifier.toUpperCase());
    }

    /**
     * SPI-facing conditional quoting: {@code null} passes through, blank is
     * returned unchanged, valid plain identifiers that are not reserved
     * keywords are returned unquoted, and everything else is wrapped in
     * backticks with one surrounding pair stripped and embedded backticks
     * doubled.
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
     * Unconditional quoting for DDL-generation call sites: {@code null} passes
     * through, every other value is wrapped in backticks with one surrounding
     * backtick pair stripped and every embedded backtick doubled.
     */
    public String quoteIdentifierAlways(String identifier) {
        if (identifier == null) {
            return null;
        }
        return "`" + escapeIdentifierContent(identifier) + "`";
    }

    /**
     * Always-quote variant that preserves the original identifier case.
     */
    @Override
    public String quoteIdentifierIgnoreCase(String identifier) {
        return quoteIdentifierAlways(identifier);
    }

    @Override
    public String removeIdentifierQuote(String identifier) {
        if (StringUtils.isBlank(identifier)) {
            return identifier;
        }
        return removePattern(identifier, CLICKHOUSE_PATTERN);
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
