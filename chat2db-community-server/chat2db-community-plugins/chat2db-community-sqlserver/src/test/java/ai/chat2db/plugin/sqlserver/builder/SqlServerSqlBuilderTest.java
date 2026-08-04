package ai.chat2db.plugin.sqlserver.builder;

import ai.chat2db.community.domain.api.config.DriverConfig;
import ai.chat2db.community.domain.api.model.result.Header;
import ai.chat2db.community.domain.api.model.result.QueryResponse;
import ai.chat2db.community.domain.api.model.result.ResultOperation;
import ai.chat2db.community.domain.api.model.view.ModifyView;
import ai.chat2db.plugin.sqlserver.SqlServerPlugin;
import ai.chat2db.spi.IPlugin;
import ai.chat2db.spi.constant.SQLConstants;
import ai.chat2db.spi.model.datasource.ConnectInfo;
import ai.chat2db.spi.sql.Chat2DBContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlServerSqlBuilderTest {

    @Test
    void shouldKeepGoDelimiterForShowplanXmlBatch() {
        SqlServerSqlBuilder builder = new SqlServerSqlBuilder();

        String sql = "SELECT * FROM uf_wtbhb WHERE lcid=1208045;";

        assertEquals("SET SHOWPLAN_XML ON;\nGO\n"
                + "SELECT * FROM uf_wtbhb WHERE lcid=1208045;\n"
                + "GO\nSET SHOWPLAN_XML OFF;", builder.dql().buildExplain(sql));
    }

    @Test
    void shouldUseTopWhenLimitingSingleRowDeleteAndUpdate() {
        SqlServerSqlBuilder builder = new SqlServerSqlBuilder();
        String where = " where [a] = 1 and [b] = 2";

        assertEquals("DELETE TOP (1) FROM [t]" + where,
                builder.appendSingleRowLimit("DELETE", "[t]", where, "DELETE FROM [t]" + where));
        assertEquals("UPDATE TOP (1) [t] set [a] = 1" + where,
                builder.appendSingleRowLimit("UPDATE", "[t]", where, "UPDATE [t] set [a] = 1" + where));
    }

    @Test
    void shouldIncludeSchemaWhenDatabaseNameIsBlank() {
        ExposedSqlServerSqlBuilder builder = new ExposedSqlServerSqlBuilder();

        assertEquals("[dbo].[orders]", builder.tableName(null, "dbo", "orders"));
        assertEquals("[analytics].[dbo].[orders]", builder.tableName("analytics", "dbo", "orders"));
    }

    @Test
    void shouldQuoteAndEscapeQualifiedViewName() {
        SqlServerSqlBuilder builder = new SqlServerSqlBuilder();
        ModifyView view = new ModifyView();
        view.setSchemaName("order] schema");
        view.setViewName("select] view");
        view.setViewBody("SELECT 1");
        view.setComment("owner's view");

        assertEquals("CREATE VIEW [order]] schema].[select]] view]\n"
                        + "AS \n"
                        + "SELECT 1 ;\n"
                        + "exec sp_addextendedproperty 'MS_Description', 'owner''s view', 'SCHEMA', "
                        + "'order] schema', 'VIEW', 'select] view'",
                builder.buildCreateView(view));
    }

    @Test
    void shouldUseExactEqualityForWildcardsInCopiedWhereClause() {
        assertEquals("WHERE value = N'50%_off'", buildCopyWhere("VARCHAR", "50%_off"));
    }

    @Test
    void shouldEscapeSingleQuoteInExactCopiedWhereClause() {
        assertEquals("WHERE value = N'O''Brien'", buildCopyWhere("VARCHAR", "O'Brien"));
    }

    @Test
    void shouldUseExactEqualityAndUnicodeLiteralForNTypes() {
        for (String columnType : List.of("NCHAR", "NVARCHAR", "NTEXT")) {
            assertEquals("WHERE value = N'\u6587\u5b57_100%'", buildCopyWhere(columnType, "\u6587\u5b57_100%"), columnType);
        }
    }

    private static String buildCopyWhere(String columnType, String value) {
        IPlugin previousPlugin = Chat2DBContext.PLUGIN_MAP.put("SQLSERVER", new SqlServerPlugin());
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setDbType("SQLSERVER");
        connectInfo.setDriverConfig(new DriverConfig());
        Chat2DBContext.putContext(connectInfo);

        try {
            QueryResponse queryResponse = new QueryResponse();
            queryResponse.setHeaderList(List.of(Header.builder()
                    .name("value")
                    .columnType(columnType)
                    .build()));
            ResultOperation operation = new ResultOperation();
            operation.setType(SQLConstants.WHERE_KEYWORD);
            operation.setDataList(List.of(value));
            operation.setSelectCols(List.of(0));
            queryResponse.setOperations(List.of(operation));
            return new SqlServerSqlBuilder().buildCopyByQueryResult(queryResponse);
        } finally {
            Chat2DBContext.removeContext();
            if (previousPlugin == null) {
                Chat2DBContext.PLUGIN_MAP.remove("SQLSERVER");
            } else {
                Chat2DBContext.PLUGIN_MAP.put("SQLSERVER", previousPlugin);
            }
        }
    }

    private static final class ExposedSqlServerSqlBuilder extends SqlServerSqlBuilder {
        private String tableName(String databaseName, String schemaName, String tableName) {
            StringBuilder script = new StringBuilder();
            buildTableName(databaseName, schemaName, tableName, script);
            return script.toString();
        }
    }
}
