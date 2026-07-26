package ai.chat2db.plugin.xugudb;

import ai.chat2db.community.domain.api.config.TableBuilderConfig;
import ai.chat2db.community.domain.api.model.metadata.Schema;
import ai.chat2db.community.domain.api.model.metadata.Table;
import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.metadata.TableIndex;
import ai.chat2db.community.domain.api.model.metadata.TableIndexColumn;
import ai.chat2db.plugin.xugudb.builder.XUGUDBSqlBuilder;
import ai.chat2db.plugin.xugudb.enums.type.XUGUDBColumnTypeEnum;
import ai.chat2db.plugin.xugudb.enums.type.XUGUDBIndexTypeEnum;
import ai.chat2db.spi.model.request.SingleInsertSqlRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XugudbSqlEscapesTest {

    private final XUGUDBSqlBuilder builder = new XUGUDBSqlBuilder();

    @Test
    void escapeSqlLiteralDoublesSingleQuotes() {
        assertEquals("o''brien", XugudbSqlEscapes.escapeSqlLiteral("o'brien"));
        assertEquals("''", XugudbSqlEscapes.escapeSqlLiteral("'"));
        assertEquals("plain", XugudbSqlEscapes.escapeSqlLiteral("plain"));
        assertEquals("", XugudbSqlEscapes.escapeSqlLiteral(null));
    }

    @Test
    void escapeIdentifierDoublesEmbeddedQuotesAndStripsSurroundingQuotes() {
        assertEquals("ta\"\"ble", XugudbSqlEscapes.escapeIdentifier("ta\"ble"));
        assertEquals("foo", XugudbSqlEscapes.escapeIdentifier("\"foo\""));
        assertEquals("fo\"\"o", XugudbSqlEscapes.escapeIdentifier("\"fo\"o\""));
        assertEquals("plain", XugudbSqlEscapes.escapeIdentifier("plain"));
        assertEquals("", XugudbSqlEscapes.escapeIdentifier(null));
    }

    @Test
    void quoteIdentifierWrapsEscapedIdentifier() {
        assertEquals("\"plain\"", XugudbSqlEscapes.quoteIdentifier("plain"));
        assertEquals("\"ta\"\"ble\"", XugudbSqlEscapes.quoteIdentifier("ta\"ble"));
    }

    @Test
    void createTableNeutralizesMaliciousSchemaName() {
        Table table = Table.builder()
                .schemaName("evil\";DROP TABLE t;--")
                .name("sample_table")
                .columnList(List.of(column("id", "INTEGER")))
                .indexList(List.of())
                .build();

        String sql = builder.buildCreateTable(table, TableBuilderConfig.defaultConfig());

        assertTrue(sql.contains("\"evil\"\";DROP TABLE t;--\".\"sample_table\""), sql);
        assertFalse(sql.contains("\"evil\";"), sql);
    }

    @Test
    void createSchemaNeutralizesMaliciousNameAndOwner() {
        Schema schema = new Schema();
        schema.setName("sch\"; DROP TABLE x; --");
        schema.setOwner("own\"; GRANT; --");

        String sql = builder.buildCreateSchema(schema);

        assertTrue(sql.contains("CREATE SCHEMA \"sch\"\"; DROP TABLE x; --\""), sql);
        assertTrue(sql.contains("AUTHORIZATION \"own\"\"; GRANT; --\""), sql);
        assertFalse(sql.contains("\"sch\";"), sql);
        assertFalse(sql.contains("AUTHORIZATION \"own\";"), sql);
    }

    @Test
    void indexScriptNeutralizesMaliciousColumnName() {
        TableIndex tableIndex = TableIndex.builder()
                .schemaName("app")
                .tableName("sample_table")
                .name("idx")
                .type("Normal")
                .columnList(List.of(TableIndexColumn.builder()
                        .columnName("col\"; DROP TABLE t; --")
                        .build()))
                .build();

        String sql = XUGUDBIndexTypeEnum.NORMAL.buildIndexScript(tableIndex);

        assertTrue(sql.contains("(\"col\"\"; DROP TABLE t; --\")"), sql);
        assertFalse(sql.contains("\"col\";"), sql);
    }

    @Test
    void maliciousDefaultValueIsRejected() {
        TableColumn column = column("id", "INTEGER");
        column.setDefaultValue("0; DROP TABLE users; --");

        assertThrows(IllegalArgumentException.class,
                () -> XUGUDBColumnTypeEnum.INTEGER.buildCreateColumnSql(column));
    }

    @Test
    void validDefaultValuesAreAccepted() {
        TableColumn numeric = column("id", "INTEGER");
        numeric.setDefaultValue("0");
        assertTrue(XUGUDBColumnTypeEnum.INTEGER.buildCreateColumnSql(numeric).contains("DEFAULT 0"));

        TableColumn keyword = column("created", "TIMESTAMP");
        keyword.setDefaultValue("CURRENT_TIMESTAMP");
        assertTrue(XUGUDBColumnTypeEnum.TIMESTAMP.buildCreateColumnSql(keyword).contains("DEFAULT CURRENT_TIMESTAMP"));
    }

    @Test
    void maliciousUnitIsRejected() {
        TableColumn column = column("name_col", "VARCHAR");
        column.setColumnSize(10);
        column.setUnit("BYTE); DROP TABLE t; --");

        assertThrows(IllegalArgumentException.class,
                () -> XUGUDBColumnTypeEnum.VARCHAR.buildCreateColumnSql(column));
    }

    @Test
    void maliciousIndexSortOrderIsRejected() {
        TableIndex tableIndex = TableIndex.builder()
                .schemaName("app")
                .tableName("sample_table")
                .name("idx")
                .type("Normal")
                .columnList(List.of(TableIndexColumn.builder()
                        .columnName("id")
                        .ascOrDesc("DESC; DROP TABLE t; --")
                        .build()))
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> XUGUDBIndexTypeEnum.NORMAL.buildIndexScript(tableIndex));
    }

    @Test
    void selectTableNeutralizesMaliciousSchemaName() {
        String sql = builder.dql().buildSelectTable(null, "evil\";DROP TABLE t;--", "sample_table");

        assertEquals("SELECT * FROM \"evil\"\";DROP TABLE t;--\".\"sample_table\"", sql);
    }

    @Test
    void insertNeutralizesMaliciousTableAndColumnNames() {
        SingleInsertSqlRequest request = SingleInsertSqlRequest.builder()
                .schemaName("app\";DROP TABLE t;--")
                .tableName("tab\";DROP TABLE t;--")
                .columnList(List.of("col\"; DROP TABLE t; --"))
                .valueList(List.of("1"))
                .build();

        String sql = builder.dml().buildInsert(request);

        assertTrue(sql.contains("INSERT INTO \"app\"\";DROP TABLE t;--\".\"tab\"\";DROP TABLE t;--\""), sql);
        assertTrue(sql.contains("(\"col\"\"; DROP TABLE t; --\")"), sql);
        assertFalse(sql.contains("INTO \"app\";"), sql);
    }

    @Test
    void columnCommentLiteralIsEscapedEndToEnd() {
        TableColumn col = column("id", "INTEGER");
        col.setComment("x'; DROP TABLE t; --");
        Table table = Table.builder()
                .schemaName("app")
                .name("sample_table")
                .columnList(List.of(col))
                .indexList(List.of())
                .build();

        String sql = builder.buildCreateTable(table, TableBuilderConfig.defaultConfig());

        assertTrue(sql.contains("IS 'x''; DROP TABLE t; --'"), sql);
        assertFalse(sql.contains("IS 'x';"), sql);
    }

    @Test
    void fallbackColumnEscapesNameAndRejectsMaliciousType() {
        TableColumn weirdName = column("na\"me", "FOOTYPE");
        assertTrue(XUGUDBColumnTypeEnum.INTEGER.buildCreateColumnSql(weirdName).startsWith("\"na\"\"me\" FOOTYPE"));

        TableColumn maliciousType = column("id", "INT); DROP TABLE t; --");
        assertThrows(IllegalArgumentException.class,
                () -> XUGUDBColumnTypeEnum.INTEGER.buildCreateColumnSql(maliciousType));
    }

    @Test
    void validatorsReturnTrimmedValues() {
        TableColumn numeric = column("id", "INTEGER");
        numeric.setDefaultValue("  0  ");
        String columnSql = XUGUDBColumnTypeEnum.INTEGER.buildCreateColumnSql(numeric);
        assertTrue(columnSql.contains("DEFAULT 0 "), columnSql);
        assertFalse(columnSql.contains("DEFAULT  0"), columnSql);

        TableColumn varchar = column("name_col", "VARCHAR");
        varchar.setColumnSize(10);
        varchar.setUnit(" BYTE ");
        String varcharSql = XUGUDBColumnTypeEnum.VARCHAR.buildCreateColumnSql(varchar);
        assertTrue(varcharSql.contains("(10 BYTE)"), varcharSql);
    }

    private static TableColumn column(String name, String type) {
        return TableColumn.builder()
                .schemaName("app")
                .tableName("sample_table")
                .name(name)
                .columnType(type)
                .nullable(1)
                .build();
    }
}
