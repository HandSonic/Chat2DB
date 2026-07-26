package ai.chat2db.plugin.oceanbase.oracle.identifier;

import ai.chat2db.plugin.oracle.identifier.OracleIdentifierProcessor;
import org.apache.commons.lang3.StringUtils;

/**
 * OceanBase (Oracle mode) dialect identifier processor.
 * <p>
 * {@link #quoteIdentifier(String)} is SPI-facing and conditional: plain,
 * non-reserved identifiers pass through unquoted so completion/matching
 * consumers keep working; anything else is wrapped with double quotes.
 * {@link #quoteIdentifierAlways(String)} is the unconditional variant reserved
 * for DDL-generation call sites that must emit quoted output.
 * String literals are escaped by single-quote doubling via {@link #escapeString(String)}.
 * Shared stateless instance available via {@link #INSTANCE} for call sites without
 * MetaData access.
 */
public class OceanbaseOracleIdentifierProcessor extends OracleIdentifierProcessor {

    public static final OceanbaseOracleIdentifierProcessor INSTANCE = new OceanbaseOracleIdentifierProcessor();

    /**
     * Conditional quoting for SPI consumers: {@code null} passes through, blank
     * is returned unchanged, a valid plain identifier that is not a reserved
     * keyword is returned unquoted; otherwise the identifier is wrapped with
     * double quotes (one surrounding quote pair stripped, embedded double
     * quotes doubled).
     */
    @Override
    public String quoteIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
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
     * The always-quote SPI variant: preserves case and unconditionally wraps.
     */
    @Override
    public String quoteIdentifierIgnoreCase(String identifier) {
        return quoteIdentifierAlways(identifier);
    }

    /**
     * Unconditional quoting for DDL-generation call sites: {@code null} passes
     * through, otherwise wraps with double quotes, stripping one surrounding
     * quote pair and doubling every embedded double quote.
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
