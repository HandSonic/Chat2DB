package ai.chat2db.plugin.dm;

import ai.chat2db.community.domain.api.model.metadata.Schema;
import ai.chat2db.community.domain.api.model.metadata.Table;
import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.metadata.TableIndex;
import ai.chat2db.community.domain.api.model.metadata.TableIndexColumn;
import ai.chat2db.plugin.dm.builder.DMSqlBuilder;
import ai.chat2db.plugin.dm.enums.type.DMColumnTypeEnum;
import ai.chat2db.plugin.dm.enums.type.DMIndexTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DMSqlEscapesTest {

    @Test
    void escapeSqlLiteralDoublesSingleQuotes() {
        assertEquals("O''Brien", DMSqlEscapes.escapeSqlLiteral("O'Brien"));
        assertEquals("x'' OR ''1''=''1", DMSqlEscapes.escapeSqlLiteral("x' OR '1'='1"));
        assertEquals("plain", DMSqlEscapes.escapeSqlLiteral("plain"));
    }

    @Test
    void quoteIdentifierDoublesEmbeddedDoubleQuotes() {
        assertEquals("\"plain\"", DMSqlEscapes.quoteIdentifier("plain"));
        assertEquals("\"we\"\"ird\"", DMSqlEscapes.quoteIdentifier("we\"ird"));
    }

    @Test
    void escapeIdentifierStripsOneSurroundingQuotePairBeforeDoubling() {
        assertEquals("already", DMSqlEscapes.escapeIdentifier("\"already\""));
        assertEquals("a\"\"b", DMSqlEscapes.escapeIdentifier("a\"b"));
        assertEquals("plain", DMSqlEscapes.escapeIdentifier("plain"));
    }

    @Test
    void createTableSqlNeutralizesMaliciousSchemaAndComment() {
        Table table = new Table();
        table.setSchemaName("s\"; DROP TABLE t; --");
        table.setName("tab");
        table.setComment("x'; DROP TABLE t; --");
        TableColumn column = new TableColumn();
        column.setName("c1");
        column.setColumnType("INT");
        table.setColumnList(List.of(column));
        table.setIndexList(List.of());

        String sql = new DMSqlBuilder().buildCreateTable(table, null);

        assertTrue(sql.contains("\"s\"\"; DROP TABLE t; --\".\"tab\""));
        assertFalse(sql.contains("\"s\"; DROP TABLE t; --\""));
        assertTrue(sql.contains("IS 'x''; DROP TABLE t; --'"));
        assertFalse(sql.contains("IS 'x'; DROP TABLE t; --'"));
    }

    @Test
    void dropTableQuotesAndEscapesTableName() {
        String sql = new DMDBManager().dropTable(null, null, null, "a\"; DROP TABLE b; --");

        assertEquals("DROP TABLE IF EXISTS \"a\"\"; DROP TABLE b; --\"", sql);
    }

    @Test
    void metaDataNameEscapesEachIdentifierPart() {
        String name = new DMMetaData().getMetaDataName("sch\"ema", "ta\"ble");

        assertEquals("\"sch\"\"ema\".\"ta\"\"ble\"", name);
    }

    @Test
    void createSchemaQuotesOwnerAsIdentifier() {
        Schema schema = new Schema();
        schema.setName("app");
        schema.setOwner("owner; DROP USER x; --");

        String sql = new DMSqlBuilder().buildCreateSchema(schema);

        assertEquals("CREATE SCHEMA \"app\" AUTHORIZATION \"owner; DROP USER x; --\"", sql);
    }

    @Test
    void indexSortOrderAcceptsAscDescAndRejectsInjection() {
        TableIndex index = new TableIndex();
        index.setSchemaName("s");
        index.setTableName("t");
        index.setName("i");
        TableIndexColumn column = new TableIndexColumn();
        column.setColumnName("c");
        column.setAscOrDesc("desc");
        index.setColumnList(List.of(column));

        assertTrue(DMIndexTypeEnum.NORMAL.buildIndexScript(index).contains("\"c\" desc"));

        column.setAscOrDesc("DESC; DROP TABLE x; --");
        assertThrows(IllegalArgumentException.class, () -> DMIndexTypeEnum.NORMAL.buildIndexScript(index));
    }

    @Test
    void quotedStringDefaultsAndUnitPassThroughUnchanged() {
        TableColumn column = new TableColumn();
        column.setName("c1");
        column.setColumnType("VARCHAR");
        column.setColumnSize(10);
        column.setUnit("BYTE");
        column.setDefaultValue("'O''Brien'");

        String sql = DMColumnTypeEnum.VARCHAR.buildCreateColumnSql(column);

        assertTrue(sql.contains("VARCHAR(10 BYTE)"));
        assertTrue(sql.contains("DEFAULT 'O''Brien'"));

        TableColumn emptyStringDefault = new TableColumn();
        emptyStringDefault.setName("c2");
        emptyStringDefault.setColumnType("VARCHAR");
        emptyStringDefault.setDefaultValue("EMPTY_STRING");

        assertTrue(DMColumnTypeEnum.VARCHAR.buildCreateColumnSql(emptyStringDefault).contains("DEFAULT ''"));
    }

    @Test
    void defaultExpressionAcceptsLegitimateAndRejectsInjection() {
        org.junit.jupiter.api.Assertions.assertEquals("'abc'", DMSqlEscapes.requireDefaultExpression("'abc'"));
        org.junit.jupiter.api.Assertions.assertEquals("'O''Brien'", DMSqlEscapes.requireDefaultExpression("'O''Brien'"));
        org.junit.jupiter.api.Assertions.assertEquals("-1.5", DMSqlEscapes.requireDefaultExpression("-1.5"));
        org.junit.jupiter.api.Assertions.assertEquals("CURRENT_TIMESTAMP", DMSqlEscapes.requireDefaultExpression("CURRENT_TIMESTAMP"));
        org.junit.jupiter.api.Assertions.assertEquals("SEQ.NEXTVAL", DMSqlEscapes.requireDefaultExpression("SEQ.NEXTVAL"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> DMSqlEscapes.requireDefaultExpression("1; DROP TABLE t"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> DMSqlEscapes.requireDefaultExpression("x' OR '1'='1"));
    }

    @Test
    void createColumnRejectsMaliciousDefault() {
        TableColumn column = new TableColumn();
        column.setName("c");
        column.setColumnType("INT");
        column.setDefaultValue("1; DROP TABLE t;--");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> DMColumnTypeEnum.INT.buildCreateColumnSql(column));
    }
}
