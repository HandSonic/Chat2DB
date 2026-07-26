package ai.chat2db.plugin.oscar.identifier;

import ai.chat2db.spi.DefaultSQLIdentifierProcessor;
import org.apache.commons.lang3.StringUtils;

import java.util.Set;

/**
 * Oscar dialect identifier processor: double-quoted identifiers with embedded-quote
 * doubling, and single-quote doubling for string literals. Shared stateless
 * instance available via {@link #INSTANCE} for call sites without MetaData access.
 */
public class OscarIdentifierProcessor extends DefaultSQLIdentifierProcessor {

    public static final OscarIdentifierProcessor INSTANCE = new OscarIdentifierProcessor();

    private static final Set<String> RESERVED_KEYWORDS = Set.of(
            "ADD", "ALL", "ALTER", "AND", "ANY", "AS", "ASC", "BEGIN", "BETWEEN", "BY", "CHAR", "CHECK",
            "COLUMN", "COMMENT", "CONNECT", "CONSTRAINT", "CREATE", "CURRENT", "DATE", "DECIMAL", "DEFAULT",
            "DELETE", "DESC", "DISTINCT", "DROP", "ELSE", "END", "EXISTS", "FLOAT", "FOR", "FROM", "FUNCTION",
            "GRANT", "GROUP", "HAVING", "IN", "INDEX", "INSERT", "INT", "INTEGER", "INTERSECT", "INTO", "IS",
            "LIKE", "MINUS", "NOT", "NULL", "NUMBER", "ON", "OR", "ORDER", "PRIMARY", "PROCEDURE", "PUBLIC",
            "RETURN", "REVOKE", "ROWNUM", "SELECT", "SET", "SMALLINT", "SYSDATE", "TABLE", "THEN", "TO",
            "TRIGGER", "UNION", "UNIQUE", "UPDATE", "USER", "VALUES", "VARCHAR", "VIEW", "WHERE", "WITH"
    );

    @Override
    public boolean isReservedKeyword(String identifier, Integer majorVersion, Integer minorVersion) {
        return RESERVED_KEYWORDS.contains(identifier);
    }

    @Override
    public String quoteIdentifier(String identifier, Integer majorVersion, Integer minorVersion) {
        return quote(identifier, true);
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return quote(identifier, true);
    }

    @Override
    public String quoteIdentifierIgnoreCase(String identifier) {
        return quote(identifier, false);
    }

    @Override
    public String convertIdentifierCase(String identifier) {
        if (StringUtils.isBlank(identifier) || isQuoteIdentifier(identifier)) {
            return identifier;
        }
        return identifier.toUpperCase();
    }

    /**
     * Escapes a value interpolated into a single-quoted SQL string literal by
     * doubling every single quote (surrounding quotes NOT added).
     */
    @Override
    public String escapeString(String str) {
        if (str == null) {
            return null;
        }
        return StringUtils.replace(str, "'", "''");
    }

    private String quote(String identifier, boolean quoteLowerCase) {
        if (StringUtils.isBlank(identifier)) {
            return identifier;
        }
        if (isQuoteIdentifier(identifier)) {
            return quoteAlways(identifier);
        }
        if (isValidIdentifier(identifier)) {
            if ((quoteLowerCase && containsLowerCase(identifier))
                    || isReservedKeyword(identifier.toUpperCase(), null, null)) {
                return quoteAlways(identifier);
            }
            return identifier;
        }
        return quoteAlways(identifier);
    }

    /**
     * Quotes an identifier with double quotes: strips one surrounding double-quote
     * pair, then doubles every embedded double quote.
     */
    private static String quoteAlways(String identifier) {
        return "\"" + escapeIdentifier(identifier) + "\"";
    }

    /**
     * Escapes identifier content for a position already surrounded by double
     * quotes: strips one surrounding quote pair, then doubles every embedded
     * double quote.
     */
    public static String escapeIdentifier(String identifier) {
        if (identifier == null) {
            return "";
        }
        String stripped = identifier;
        if (stripped.length() >= 2 && stripped.startsWith("\"") && stripped.endsWith("\"")) {
            stripped = stripped.substring(1, stripped.length() - 1);
        }
        return StringUtils.replace(stripped, "\"", "\"\"");
    }
}
