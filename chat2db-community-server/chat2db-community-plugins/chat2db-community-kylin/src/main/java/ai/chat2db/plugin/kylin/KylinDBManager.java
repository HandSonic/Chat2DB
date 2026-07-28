package ai.chat2db.plugin.kylin;

import ai.chat2db.community.domain.api.model.async.AsyncContext;
import ai.chat2db.community.domain.api.model.metadata.Table;
import ai.chat2db.spi.IDbManager;
import ai.chat2db.spi.DefaultDBManager;
import ai.chat2db.spi.model.request.TablesRequest;
import ai.chat2db.spi.sql.Chat2DBContext;
import cn.hutool.core.date.DateUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

import static cn.hutool.core.date.DatePattern.NORM_DATETIME_PATTERN;

public class KylinDBManager extends DefaultDBManager implements IDbManager {

    @Override
    public void exportDatabase(Connection connection, String databaseName, String schemaName,
                               AsyncContext asyncContext) throws SQLException {
        asyncContext.write(String.format(EXPORT_TITLE, DateUtil.format(new Date(), NORM_DATETIME_PATTERN)));
        asyncContext.info(DateUtil.formatDateTime(new Date()) + ":Exporting tables");
        exportTables(connection, databaseName, schemaName, asyncContext);
        asyncContext.setProgress(50);
    }

    private void exportTables(Connection connection, String databaseName, String schemaName,
                              AsyncContext asyncContext) throws SQLException {
        // Unlike DefaultDBManager, do not emit MySQL-only SET FOREIGN_KEY_CHECKS statements.
        List<Table> tables = Chat2DBContext.getDbMetaData().tables(connection,
                new TablesRequest(databaseName, schemaName, null));
        for (Table table : tables) {
            exportTable(connection, databaseName, schemaName, table.getName(), asyncContext);
        }
    }
}
