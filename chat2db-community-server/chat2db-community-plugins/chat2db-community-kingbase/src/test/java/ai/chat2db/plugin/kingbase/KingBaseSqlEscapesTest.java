package ai.chat2db.plugin.kingbase;

import ai.chat2db.community.domain.api.enums.plugin.EditStatusEnum;
import ai.chat2db.community.domain.api.model.metadata.Database;
import ai.chat2db.community.domain.api.model.metadata.Schema;
import ai.chat2db.community.domain.api.model.metadata.Table;
import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.metadata.TableIndex;
import ai.chat2db.community.domain.api.model.metadata.TableIndexColumn;
import ai.chat2db.plugin.kingbase.builder.KingBaseSqlBuilder;
import ai.chat2db.plugin.kingbase.enums.type.KingBaseColumnTypeEnum;
import ai.chat2db.plugin.kingbase.enums.type.KingBaseIndexTypeEnum;
import ai.chat2db.plugin.kingbase.identifier.KingBaseSQLIdentifierProcessor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KingBaseSqlEscapesTest {

    @Test
    void escapeSqlLiteralDoublesSingleQuotes() {
        assertNull(KingBaseSqlEscapes.escapeSqlLiteral(null));
        assertEquals("plain", KingBaseSqlEscapes.escapeSqlLiteral("plain"));
        assertEquals("O''Brien", KingBaseSqlEscapes.escapeSqlLiteral("O'Brien"));
        assertEquals("a''; DROP TABLE t; --", KingBaseSqlEscapes.escapeSqlLiteral("a'; DROP TABLE t; --"));
    }

    @Test
    void escapeIdentifierStripsOnePairAndDoublesEmbeddedQuotes() {
        assertNull(KingBaseSqlEscapes.escapeIdentifier(null));
        assertEquals("plain", KingBaseSqlEscapes.escapeIdentifier("plain"));
        assertEquals("we\"\"name", KingBaseSqlEscapes.escapeIdentifier("we\"name"));
        assertEquals("quoted", KingBaseSqlEscapes.escapeIdentifier("\"quoted\""));
    }

    @Test
    void quoteIdentifierWrapsAndDoublesEmbeddedQuotes() {
        assertNull(KingBaseSqlEscapes.quoteIdentifier(null));
        assertEquals("\"plain\"", KingBaseSqlEscapes.quoteIdentifier("plain"));
        assertEquals("\"we\"\"name\"", KingBaseSqlEscapes.quoteIdentifier("we\"name"));
        assertEquals("\"evil\"\"; DROP TABLE t; --\"",
                KingBaseSqlEscapes.quoteIdentifier("evil\"; DROP TABLE t; --"));
    }

    @Test
    void expressionWhitelistAcceptsLegitimateValues() {
        assertTrue(KingBaseSqlEscapes.isSafeSqlExpression("0"));
        assertTrue(KingBaseSqlEscapes.isSafeSqlExpression("-1"));
        assertTrue(KingBaseSqlEscapes.isSafeSqlExpression("3.14"));
        assertTrue(KingBaseSqlEscapes.isSafeSqlExpression("now()"));
        assertTrue(KingBaseSqlEscapes.isSafeSqlExpression("CURRENT_TIMESTAMP"));
        assertTrue(KingBaseSqlEscapes.isSafeSqlExpression("nextval('seq'::regclass)"));
        assertTrue(KingBaseSqlEscapes.isSafeSqlExpression("'quoted string'"));
        assertTrue(KingBaseSqlEscapes.isSafeSqlExpression("'it''s'"));
        assertTrue(KingBaseSqlEscapes.isSafeSqlExpression("'2024-01-01 00:00:00'"));
        assertTrue(KingBaseSqlEscapes.isSafeSqlExpression("true"));
        assertTrue(KingBaseSqlEscapes.isSafeSqlExpression("UTF8"));
        assertTrue(KingBaseSqlEscapes.isSafeSqlExpression("'UTF8'"));
        assertTrue(KingBaseSqlEscapes.isSafeSqlExpression("GB18030"));
        assertEquals("now()", KingBaseSqlEscapes.requireSafeExpression("now()", "test"));
    }

    @Test
    void expressionWhitelistRejectsInjection() {
        assertFalse(KingBaseSqlEscapes.isSafeSqlExpression(null));
        assertFalse(KingBaseSqlEscapes.isSafeSqlExpression(""));
        assertFalse(KingBaseSqlEscapes.isSafeSqlExpression("0; DROP TABLE users--"));
        assertFalse(KingBaseSqlEscapes.isSafeSqlExpression("1--"));
        assertFalse(KingBaseSqlEscapes.isSafeSqlExpression("x/*"));
        assertFalse(KingBaseSqlEscapes.isSafeSqlExpression("*/"));
        assertFalse(KingBaseSqlEscapes.isSafeSqlExpression("$$body$$"));
        assertFalse(KingBaseSqlEscapes.isSafeSqlExpression("'unterminated"));
        assertThrows(IllegalArgumentException.class,
                () -> KingBaseSqlEscapes.requireSafeExpression("0; DROP TABLE users--", "test"));
    }

    @Test
    void createTableNeutralizesMaliciousNamesAndComments() {
        Table table = new Table();
        table.setName("evil\"; DROP TABLE t; --");
        table.setComment("x'; DROP TABLE t; --");

        TableColumn column = new TableColumn();
        column.setName("c1");
        column.setColumnType("VARCHAR");
        table.setColumnList(List.of(column));

        TableIndex index = new TableIndex();
        index.setName("idx\"evil");
        index.setType("Normal");
        index.setTableName("evil\"; DROP TABLE t; --");
        TableIndexColumn indexColumn = new TableIndexColumn();
        indexColumn.setColumnName("c1");
        index.setColumnList(List.of(indexColumn));
        table.setIndexList(List.of(index));

        String script = new KingBaseSqlBuilder().buildCreateTable(table, null);
        assertTrue(script.contains("CREATE TABLE \"evil\"\"; DROP TABLE t; --\""), script);
        assertTrue(script.contains("IS 'x''; DROP TABLE t; --'"), script);
        assertTrue(script.contains("\"idx\"\"evil\""), script);
        assertTrue(script.contains("ON \"evil\"\"; DROP TABLE t; --\""), script);
    }

    @Test
    void alterTableRenameNeutralizesMaliciousNames() {
        Table oldTable = new Table();
        oldTable.setName("old_t");
        oldTable.setColumnList(List.of());
        oldTable.setIndexList(List.of());
        Table newTable = new Table();
        newTable.setName("new\"; DROP TABLE t; --");
        newTable.setColumnList(List.of());
        newTable.setIndexList(List.of());

        String script = new KingBaseSqlBuilder().buildAlterTable(oldTable, newTable);
        assertTrue(script.contains("ALTER TABLE \"old_t\""), script);
        assertTrue(script.contains("RENAME TO \"new\"\"; DROP TABLE t; --\""), script);
    }

    @Test
    void createDatabaseEscapesAndValidates() {
        Database database = new Database();
        database.setName("db\"; DROP TABLE t; --");
        database.setCharset("UTF8");
        database.setComment("c'; DROP TABLE t; --");

        String script = new KingBaseSqlBuilder().buildCreateDatabase(database);
        assertTrue(script.contains("CREATE DATABASE \"db\"\"; DROP TABLE t; --\""), script);
        assertTrue(script.contains("IS 'c''; DROP TABLE t; --'"), script);

        Database badCharset = new Database();
        badCharset.setName("db2");
        badCharset.setCharset("UTF8; DROP TABLE t; --");
        assertThrows(IllegalArgumentException.class,
                () -> new KingBaseSqlBuilder().buildCreateDatabase(badCharset));

        Database quotedCharset = new Database();
        quotedCharset.setName("db3");
        quotedCharset.setCharset("'UTF8'");
        String ok = new KingBaseSqlBuilder().buildCreateDatabase(quotedCharset);
        assertTrue(ok.contains("ENCODING  'UTF8'"), ok);
    }

    @Test
    void createSchemaNeutralizesMaliciousNames() {
        Schema schema = new Schema();
        schema.setName("sch\"; x; --");
        String script = new KingBaseSqlBuilder().buildCreateSchema(schema);
        assertTrue(script.contains("CREATE SCHEMA \"sch\"\"; x; --\""), script);
        assertTrue(script.contains("AUTHORIZATION \"SYSTEM\""), script);
    }

    @Test
    void columnTypeEnumEscapesNamesCommentsAndDefaults() {
        TableColumn column = new TableColumn();
        column.setName("c\"; x--");
        column.setColumnType("VARCHAR");
        column.setDefaultValue("O'Brien");
        String createColumn = KingBaseColumnTypeEnum.VARCHAR.buildCreateColumnSql(column);
        assertTrue(createColumn.startsWith("\"c\"\"; x--\" VARCHAR"), createColumn);
        assertTrue(createColumn.contains("DEFAULT 'O''Brien'"), createColumn);

        TableColumn commentColumn = new TableColumn();
        commentColumn.setName("c1");
        commentColumn.setTableName("t1");
        commentColumn.setComment("y'; DROP TABLE t; --");
        String comment = KingBaseColumnTypeEnum.VARCHAR.buildComment(commentColumn, KingBaseColumnTypeEnum.VARCHAR);
        assertEquals("COMMENT ON COLUMN \"t1\".\"c1\" IS 'y''; DROP TABLE t; --';", comment);

        TableColumn badDefault = new TableColumn();
        badDefault.setName("n");
        badDefault.setColumnType("INTEGER");
        badDefault.setDefaultValue("1; DROP TABLE t--");
        assertThrows(IllegalArgumentException.class,
                () -> KingBaseColumnTypeEnum.INTEGER.buildCreateColumnSql(badDefault));

        TableColumn okDefault = new TableColumn();
        okDefault.setName("n");
        okDefault.setColumnType("INTEGER");
        okDefault.setDefaultValue("-1");
        String ok = KingBaseColumnTypeEnum.INTEGER.buildCreateColumnSql(okDefault);
        assertTrue(ok.contains("DEFAULT -1"), ok);

        TableColumn quotedDefault = new TableColumn();
        quotedDefault.setName("n");
        quotedDefault.setColumnType("TEXT");
        quotedDefault.setDefaultValue("'quoted string'");
        String okQuoted = KingBaseColumnTypeEnum.TEXT.buildCreateColumnSql(quotedDefault);
        assertTrue(okQuoted.contains("DEFAULT 'quoted string'"), okQuoted);
    }

    @Test
    void indexTypeEnumEscapesNamesAndComments() {
        TableIndex index = new TableIndex();
        index.setName("i\"; x--");
        index.setComment("y'; z--");
        String comment = KingBaseIndexTypeEnum.NORMAL.buildIndexComment(index);
        assertEquals("COMMENT ON INDEX \"i\"\"; x--\" IS 'y''; z--';", comment);

        TableIndex fk = new TableIndex();
        fk.setName("fk1");
        fk.setForeignSchemaName("s\"; x--");
        fk.setForeignTableName("ft");
        fk.setForeignColumnNamelist(List.of("c1"));
        TableIndexColumn fkColumn = new TableIndexColumn();
        fkColumn.setColumnName("c1");
        fk.setColumnList(List.of(fkColumn));
        String fkScript = KingBaseIndexTypeEnum.FOREIGN.buildIndexScript(fk);
        assertTrue(fkScript.contains("REFERENCES \"s\"\"; x--\".\"ft\" (\"c1\")"), fkScript);

        TableIndex drop = new TableIndex();
        drop.setOldName("o\"; x--");
        drop.setEditStatus(EditStatusEnum.DELETE.name());
        String dropScript = KingBaseIndexTypeEnum.NORMAL.buildModifyIndex(drop);
        assertEquals("DROP INDEX \"o\"\"; x--\"", dropScript);
    }

    @Test
    void dbManagerQuotesObjectNames() {
        String sql = new KingBaseDBManager().dropTable(null, null, null, "t\"; x--");
        assertEquals("drop table if exists \"t\"\"; x--\"", sql);
    }

    @Test
    void metaDataNameDoublesEmbeddedQuotes() {
        String name = new KingBaseMetaData().getMetaDataName("s", "we\"ird");
        assertEquals("\"s\".\"we\"\"ird\"", name);
    }

    @Test
    void identifierProcessorDoublesEmbeddedQuotes() {
        KingBaseSQLIdentifierProcessor processor = new KingBaseSQLIdentifierProcessor();
        assertEquals("plain", processor.quoteIdentifier("plain"));
        assertEquals("\"UPPER\"", processor.quoteIdentifier("UPPER"));
        assertEquals("\"we\"\"name\"", processor.quoteIdentifier("we\"name"));
    }

    @Test
    void indexMethodAcceptsKnownAndRejectsInjection() {
        org.junit.jupiter.api.Assertions.assertEquals("btree", KingBaseSqlEscapes.requireIndexMethod("btree"));
        org.junit.jupiter.api.Assertions.assertEquals("gin", KingBaseSqlEscapes.requireIndexMethod("gin"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> KingBaseSqlEscapes.requireIndexMethod("btree); DROP TABLE t;--"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> KingBaseSqlEscapes.requireIndexMethod("btree USING x"));
    }
}
