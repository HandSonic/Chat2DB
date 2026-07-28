package ai.chat2db.plugin.kylin;

import ai.chat2db.spi.IDbMetaData;
import ai.chat2db.spi.DefaultMetaService;
import ai.chat2db.spi.util.DBStructUtils;

import java.sql.Connection;

public class KylinMetaData extends DefaultMetaService implements IDbMetaData {

    @Override
    public String tableDDL(Connection connection, String databaseName, String schemaName, String tableName) {
        // Kylin has no SHOW CREATE TABLE; build the DDL from JDBC metadata instead
        // (same approach as GenericMetaData).
        return DBStructUtils.getTableDdl(connection, databaseName, schemaName, tableName);
    }
}
