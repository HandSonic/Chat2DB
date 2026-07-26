package ai.chat2db.plugin.sqlite.identifier;

import ai.chat2db.plugin.sqlite.SqliteSqlEscapes;
import ai.chat2db.spi.DefaultSQLIdentifierProcessor;

public class SqliteIdentifierProcessor extends DefaultSQLIdentifierProcessor {

    @Override
    public String quoteIdentifier(String identifier, Integer majorVersion, Integer minorVersion) {
        if (isValidIdentifier(identifier)) {
            return identifier;
        }
        return SqliteSqlEscapes.quoteIdentifier(identifier);
    }

    @Override
    public String quoteIdentifier(String identifier) {
        if (isValidIdentifier(identifier)) {
            return identifier;
        }
        return SqliteSqlEscapes.quoteIdentifier(identifier);
    }
}
