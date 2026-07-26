package ai.chat2db.plugin.sundb;

import ai.chat2db.community.domain.api.model.metadata.Schema;
import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.metadata.TableIndex;
import ai.chat2db.community.domain.api.model.metadata.TableIndexColumn;
import ai.chat2db.plugin.sundb.builder.SUNDBSqlBuilder;
import ai.chat2db.plugin.sundb.enums.type.SUNDBColumnTypeEnum;
import ai.chat2db.plugin.sundb.enums.type.SUNDBIndexTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the SQL-injection hardening helpers (#1914): single-quote doubling
 * for string literals, double-quote doubling for quoted identifiers, and
 * representative SQL-building paths fed with names containing quote chars.
 */
class SUNDBSqlEscapesTest {

    @Test
    void escapeSqlLiteralDoublesSingleQuotes() {
        assertEquals("O''Brien", SUNDBSqlEscapes.escapeSqlLiteral("O'Brien"));
        assertEquals("a''b''c", SUNDBSqlEscapes.escapeSqlLiteral("a'b'c"));
        assertEquals("plain", SUNDBSqlEscapes.escapeSqlLiteral("plain"));
        assertEquals("", SUNDBSqlEscapes.escapeSqlLiteral(null));
    }

    @Test
    void escapeIdentifierDoublesEmbeddedDoubleQuotesAndStripsOuterQuotes() {
        assertEquals("we\"\"ird", SUNDBSqlEscapes.escapeIdentifier("we\"ird"));
        assertEquals("abc", SUNDBSqlEscapes.escapeIdentifier("\"abc\""));
        assertEquals("a\"\"b", SUNDBSqlEscapes.escapeIdentifier("\"a\"b\""));
        assertEquals("", SUNDBSqlEscapes.escapeIdentifier(null));
        assertEquals("\"we\"\"ird\"", SUNDBSqlEscapes.quoteIdentifier("we\"ird"));
    }

    @Test
    void getMetaDataNameNeutralizesQuotesInNames() {
        String name = new SUNDBMetaData().getMetaDataName("sch\"ema", "ta\"ble");

        assertEquals("\"sch\"\"ema\".\"ta\"\"ble\"", name);
        assertFalse(name.contains("sch\"ema"));
    }

    @Test
    void buildCreateSchemaQuotesAndEscapesNameAndOwner() {
        Schema schema = new Schema();
        schema.setName("we\"ird");
        schema.setOwner("ad\"min");

        String sql = new SUNDBSqlBuilder().buildCreateSchema(schema);

        assertEquals("CREATE SCHEMA \"we\"\"ird\" AUTHORIZATION \"ad\"\"min\"", sql);
        assertFalse(sql.contains("we\"ird"));
    }

    @Test
    void dropTableEscapesSchemaAndTableIdentifiers() {
        String sql = new SUNDBDBManager().dropTable(null, "db", "sch\"ema", "ta\"ble");

        assertEquals("DROP TABLE IF EXISTS \"sch\"\"ema\".\"ta\"\"ble\"", sql);
        assertFalse(sql.contains("sch\"ema"));
    }

    @Test
    void buildIndexScriptEscapesSchemaTableIndexAndColumnNames() {
        TableIndex tableIndex = new TableIndex();
        tableIndex.setSchemaName("sch\"ema");
        tableIndex.setTableName("ta\"ble");
        tableIndex.setName("idx\"name");
        TableIndexColumn column = new TableIndexColumn();
        column.setColumnName("co\"l");
        tableIndex.setColumnList(List.of(column));

        String sql = SUNDBIndexTypeEnum.NORMAL.buildIndexScript(tableIndex);

        assertEquals("CREATE INDEX \"sch\"\"ema\".\"idx\"\"name\" ON \"sch\"\"ema\".\"ta\"\"ble\" (\"co\"\"l\")", sql);
        assertFalse(sql.contains("ta\"ble\""));
    }

    @Test
    void buildCreateColumnSqlEscapesColumnName() {
        TableColumn column = new TableColumn();
        column.setName("co\"l");
        column.setColumnType("BOOLEAN");

        String sql = SUNDBColumnTypeEnum.BOOLEAN.buildCreateColumnSql(column);

        assertTrue(sql.startsWith("\"co\"\"l\" "));
    }

    @Test
    void buildCreateColumnSqlAcceptsValidUnitAndRejectsInjectedUnit() {
        TableColumn valid = new TableColumn();
        valid.setName("c1");
        valid.setColumnType("VARCHAR");
        valid.setColumnSize(10);
        valid.setUnit("byte");
        assertTrue(SUNDBColumnTypeEnum.VARCHAR.buildCreateColumnSql(valid).contains("VARCHAR(10 byte)"));

        TableColumn malicious = new TableColumn();
        malicious.setName("c1");
        malicious.setColumnType("VARCHAR");
        malicious.setColumnSize(10);
        malicious.setUnit("CHAR); DROP TABLE x--");
        assertThrows(IllegalArgumentException.class,
                () -> SUNDBColumnTypeEnum.VARCHAR.buildCreateColumnSql(malicious));
    }

    @Test
    void buildIndexScriptCanonicalizesAscOrDescAndRejectsInjection() {
        TableIndex tableIndex = new TableIndex();
        tableIndex.setSchemaName("s");
        tableIndex.setTableName("t");
        tableIndex.setName("i");
        TableIndexColumn column = new TableIndexColumn();
        column.setColumnName("c");
        column.setAscOrDesc("desc");
        tableIndex.setColumnList(List.of(column));
        assertTrue(SUNDBIndexTypeEnum.NORMAL.buildIndexScript(tableIndex).contains("(\"c\" DESC)"));

        TableIndexColumn malicious = new TableIndexColumn();
        malicious.setColumnName("c");
        malicious.setAscOrDesc("DESC); DROP TABLE \"U\"; --");
        tableIndex.setColumnList(List.of(malicious));
        assertThrows(IllegalArgumentException.class,
                () -> SUNDBIndexTypeEnum.NORMAL.buildIndexScript(tableIndex));
    }

    @Test
    void buildCreateColumnSqlAcceptsValidDefaultAndRejectsInjectedDefault() {
        TableColumn valid = new TableColumn();
        valid.setName("c1");
        valid.setColumnType("BOOLEAN");
        valid.setDefaultValue("CURRENT_TIMESTAMP");
        assertTrue(SUNDBColumnTypeEnum.BOOLEAN.buildCreateColumnSql(valid).contains("DEFAULT CURRENT_TIMESTAMP"));

        TableColumn malicious = new TableColumn();
        malicious.setName("c1");
        malicious.setColumnType("BOOLEAN");
        malicious.setDefaultValue("1; DROP TABLE x--");
        assertThrows(IllegalArgumentException.class,
                () -> SUNDBColumnTypeEnum.BOOLEAN.buildCreateColumnSql(malicious));
    }
}
