package ai.chat2db.plugin.presto;

import ai.chat2db.spi.DefaultSQLExecutor;
import ai.chat2db.spi.DefaultMetaService;
import ai.chat2db.spi.IDbMetaData;
import ai.chat2db.spi.ISQLIdentifierProcessor;

import java.sql.Connection;

public class PrestoMetaData extends DefaultMetaService implements IDbMetaData {

    @Override
    public String tableDDL(Connection connection, String databaseName, String schemaName, String tableName) {
        ISQLIdentifierProcessor identifierProcessor = getSQLIdentifierProcessor();
        String sql = "SHOW CREATE TABLE "
                + identifierProcessor.quoteIdentifierAlways(databaseName) + "."
                + identifierProcessor.quoteIdentifierAlways(schemaName) + "."
                + identifierProcessor.quoteIdentifierAlways(tableName);
        return DefaultSQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            if (resultSet.next()) {
                return resultSet.getString(1);
            }
            return null;
        });
    }
}
