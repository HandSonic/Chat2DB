package ai.chat2db.plugin.clickhouse;

import ai.chat2db.community.domain.api.model.metadata.Database;
import ai.chat2db.community.domain.api.model.metadata.Table;
import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.plugin.clickhouse.builder.ClickHouseSqlBuilder;
import ai.chat2db.plugin.clickhouse.enums.type.ClickHouseColumnTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClickHouseSqlEscapesTest {

    @Test
    void shouldDoubleSingleQuotesInLiterals() {
        assertEquals("owner''s", ClickHouseSqlEscapes.escapeSqlLiteral("owner's"));
        assertEquals("", ClickHouseSqlEscapes.escapeSqlLiteral(null));
    }

    @Test
    void shouldEscapeBackslashesInLiterals() {
        assertEquals("a\\\\b''c", ClickHouseSqlEscapes.escapeSqlLiteral("a\\b'c"));
    }

    @Test
    void shouldDoubleBackticksInIdentifiers() {
        assertEquals("`a``b`", ClickHouseSqlEscapes.quoteIdentifier("a`b"));
        assertEquals("`plain`", ClickHouseSqlEscapes.quoteIdentifier("plain"));
        assertEquals("``", ClickHouseSqlEscapes.quoteIdentifier(null));
    }

    @Test
    void shouldStripSurroundingBackticksBeforeDoubling() {
        assertEquals("`a``b`", ClickHouseSqlEscapes.quoteIdentifier("`a`b`"));
    }

    @Test
    void shouldNeutralizeMaliciousTableNameInCreateTable() {
        ClickHouseSqlBuilder builder = new ClickHouseSqlBuilder();
        Table table = new Table();
        table.setName("evil` , (id Int32) ENGINE=Memory; -- ");
        table.setDatabaseName("db`x");
        table.setColumnList(new ArrayList<>());
        table.setIndexList(new ArrayList<>());

        String sql = builder.buildCreateTable(table, null);

        assertTrue(sql.contains("`db``x`.`evil`` , (id Int32) ENGINE=Memory; -- `"),
                "identifier backticks must be doubled: " + sql);
    }

    @Test
    void shouldEscapeCommentLiteralInCreateTable() {
        ClickHouseSqlBuilder builder = new ClickHouseSqlBuilder();
        Table table = new Table();
        table.setName("t");
        table.setColumnList(new ArrayList<>());
        table.setIndexList(new ArrayList<>());
        table.setComment("x' OR '1'='1");

        String sql = builder.buildCreateTable(table, null);

        assertTrue(sql.contains("COMMENT 'x'' OR ''1''=''1'"), "comment quote must be doubled: " + sql);
    }

    @Test
    void shouldEscapeColumnNameAndCommentInCreateColumn() {
        TableColumn column = new TableColumn();
        column.setName("col`drop");
        column.setColumnType("STRING");
        column.setComment("it's");

        String sql = ClickHouseColumnTypeEnum.String.buildCreateColumnSql(column);

        assertTrue(sql.startsWith("`col``drop` "), "column identifier backticks must be doubled: " + sql);
        assertTrue(sql.contains("COMMENT 'it''s'"), "column comment quote must be doubled: " + sql);
    }

    @Test
    void shouldRejectMaliciousEngine() {
        ClickHouseSqlBuilder builder = new ClickHouseSqlBuilder();
        Table table = new Table();
        table.setName("t");
        table.setColumnList(new ArrayList<>());
        table.setIndexList(new ArrayList<>());
        table.setEngine("Memory; DROP TABLE users");

        assertThrows(IllegalArgumentException.class, () -> builder.buildCreateTable(table, null));
    }

    @Test
    void shouldAcceptKnownEngine() {
        ClickHouseSqlBuilder builder = new ClickHouseSqlBuilder();
        Table table = new Table();
        table.setName("t");
        table.setColumnList(new ArrayList<>());
        table.setIndexList(new ArrayList<>());
        table.setEngine("MergeTree");

        String sql = builder.buildCreateTable(table, null);

        assertTrue(sql.contains("ENGINE=MergeTree"), sql);
    }

    @Test
    void shouldNeutralizeMaliciousDatabaseNameInCreateDatabase() {
        ClickHouseSqlBuilder builder = new ClickHouseSqlBuilder();
        Database database = new Database();
        database.setName("db`; DROP TABLE x; --");
        database.setComment("c' OR '1'='1");

        String sql = builder.buildCreateDatabase(database);

        assertTrue(sql.contains("CREATE DATABASE `db``; DROP TABLE x; --`"), sql);
        assertTrue(sql.contains("COMMENT 'c'' OR ''1''=''1'"), sql);
    }

    @Test
    void shouldQuoteMetadataNameParts() {
        ClickHouseMetaData metaData = new ClickHouseMetaData();

        assertEquals("`db`.`ta``ble`", metaData.getMetaDataName("db", "ta`ble"));
    }

    @Test
    void shouldAcceptNumericAndFunctionDefaults() {
        TableColumn column = new TableColumn();
        column.setName("c");
        column.setColumnType("INT32");
        column.setDefaultValue("-1");

        String sql = ClickHouseColumnTypeEnum.Int32.buildCreateColumnSql(column);

        assertTrue(sql.contains("DEFAULT -1"), sql);
    }

    @Test
    void shouldAcceptQuotedStringDefaults() {
        TableColumn column = new TableColumn();
        column.setName("c");
        column.setColumnType("String");
        column.setDefaultValue("'abc'");

        String sql = ClickHouseColumnTypeEnum.String.buildCreateColumnSql(column);

        assertTrue(sql.contains("DEFAULT 'abc'"), sql);
    }

    @Test
    void shouldEscapeQuotedStringDefaultContent() {
        TableColumn column = new TableColumn();
        column.setName("c");
        column.setColumnType("String");
        column.setDefaultValue("'a');DROP TABLE t;--'");

        String sql = ClickHouseColumnTypeEnum.String.buildCreateColumnSql(column);

        assertTrue(sql.contains("DEFAULT 'a'');DROP TABLE t;--'"), sql);
    }

    @Test
    void shouldResolveMixedCaseAndDigitTypeNames() {
        TableColumn mixed = new TableColumn();
        mixed.setName("c1");
        mixed.setColumnType("String");
        assertTrue(ClickHouseColumnTypeEnum.buildCreateColumnSqlSafely(mixed).startsWith("`c1` String"),
                "mixed-case type must resolve to the enum");

        TableColumn digits = new TableColumn();
        digits.setName("c2");
        digits.setColumnType("Int32");
        assertTrue(ClickHouseColumnTypeEnum.buildCreateColumnSqlSafely(digits).startsWith("`c2` Int32"),
                "digit-containing type must resolve to the enum");
    }

    @Test
    void shouldEmitValidatedFallbackForUnknownType() {
        TableColumn nested = new TableColumn();
        nested.setName("c");
        nested.setColumnType("Array(Nullable(String))");
        String sql = ClickHouseColumnTypeEnum.buildCreateColumnSqlSafely(nested);
        assertTrue(sql.startsWith("`c` Array(Nullable(String))"), sql);

        TableColumn malicious = new TableColumn();
        malicious.setName("c");
        malicious.setColumnType("Int32); DROP TABLE x; --");
        assertThrows(IllegalArgumentException.class,
                () -> ClickHouseColumnTypeEnum.buildCreateColumnSqlSafely(malicious));

        TableColumn breakout = new TableColumn();
        breakout.setName("c");
        breakout.setColumnType("Int32, injected Int32");
        assertThrows(IllegalArgumentException.class,
                () -> ClickHouseColumnTypeEnum.buildCreateColumnSqlSafely(breakout));
    }

    @Test
    void shouldRejectEngineAndDefaultParenBreakout() {
        ClickHouseSqlBuilder builder = new ClickHouseSqlBuilder();
        Table table = new Table();
        table.setName("t");
        table.setColumnList(new ArrayList<>());
        table.setIndexList(new ArrayList<>());
        table.setEngine("Memory() ORDER BY tuple() -- x");
        assertThrows(IllegalArgumentException.class, () -> builder.buildCreateTable(table, null));

        TableColumn column = new TableColumn();
        column.setName("c");
        column.setColumnType("INT32");
        column.setDefaultValue("f(1)) ENGINE=Memory -- x");
        assertThrows(IllegalArgumentException.class,
                () -> ClickHouseColumnTypeEnum.Int32.buildCreateColumnSql(column));
    }

    @Test
    void shouldEscapeQuotedDefaultLiteral() {
        TableColumn column = new TableColumn();
        column.setName("d");
        column.setColumnType("DATE");
        column.setDefaultValue("2024-01-01' OR '1'='1");

        String sql = ClickHouseColumnTypeEnum.Date.buildCreateColumnSql(column);

        assertTrue(sql.contains("DEFAULT '2024-01-01'' OR ''1''=''1'"), sql);
    }
}
