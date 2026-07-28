package ai.chat2db.plugin.presto;

import ai.chat2db.spi.DefaultSQLExecutor;
import ai.chat2db.spi.IDbMetaData;
import ai.chat2db.spi.DefaultMetaService;

import java.sql.Connection;

public class PrestoMetaData extends DefaultMetaService implements IDbMetaData {

    @Override
    public String tableDDL(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = "SHOW CREATE TABLE " + schemaName + "." + tableName;
        return DefaultSQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            if (resultSet.next()) {
                // Presto returns the DDL in a single "Create Table" column
                return resultSet.getString(1);
            }
            return null;
        });
    }
}
