package ai.chat2db.plugin.presto;

import ai.chat2db.spi.DefaultSQLExecutor;
import ai.chat2db.spi.IDbMetaData;
import ai.chat2db.spi.DefaultMetaService;

import java.sql.Connection;

public class PrestoMetaData extends DefaultMetaService implements IDbMetaData {

    @Override
    public String tableDDL(Connection connection, String databaseName, String schemaName, String tableName) {
        // Quote each part with Presto double-quote identifiers (embedded quotes doubled)
        // so reserved words, spaces, punctuation and case-sensitive names resolve.
        String sql = "SHOW CREATE TABLE " + quoteIdentifier(schemaName) + "." + quoteIdentifier(tableName);
        return DefaultSQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            if (resultSet.next()) {
                // Presto returns the DDL in a single "Create Table" column
                return resultSet.getString(1);
            }
            return null;
        });
    }

    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
