package ai.chat2db.plugin.db2;

import ai.chat2db.community.domain.api.model.metadata.Schema;
import ai.chat2db.community.domain.api.model.metadata.Table;
import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.metadata.TableIndex;
import ai.chat2db.plugin.db2.builder.DB2SqlBuilder;
import ai.chat2db.plugin.db2.enums.type.DB2ColumnTypeEnum;
import ai.chat2db.plugin.db2.enums.type.DB2IndexTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Db2SqlEscapesTest {

    @Test
    void escapeSqlLiteralDoublesSingleQuotes() {
        assertEquals("O''Brien", Db2SqlEscapes.escapeSqlLiteral("O'Brien"));
        assertEquals("plain", Db2SqlEscapes.escapeSqlLiteral("plain"));
        assertEquals("", Db2SqlEscapes.escapeSqlLiteral(null));
    }

    @Test
    void escapeIdentifierDoublesDoubleQuotesAndStripsSurroundingQuotes() {
        assertEquals("we\"\"ird", Db2SqlEscapes.escapeIdentifier("we\"ird"));
        assertEquals("abc", Db2SqlEscapes.escapeIdentifier("\"abc\""));
        assertEquals("", Db2SqlEscapes.escapeIdentifier(null));
        assertEquals("\"a\"\"b\"", Db2SqlEscapes.quoteIdentifier("a\"b"));
    }

    @Test
    void createSchemaNeutralizesMaliciousNameAndComment() {
        Schema schema = new Schema();
        schema.setName("bad\"name");
        schema.setComment("x'; DROP TABLE t; --");

        String sql = new DB2SqlBuilder().buildCreateSchema(schema);

        assertEquals("CREATE SCHEMA \"bad\"\"name\";\nCOMMENT ON SCHEMA \"bad\"\"name\" IS 'x''; DROP TABLE t; --';", sql);
    }

    @Test
    void createTableNeutralizesMaliciousTableName() {
        Table table = new Table();
        table.setSchemaName("S");
        table.setName("evil\" , \"x");
        table.setColumnList(new ArrayList<>());
        table.setIndexList(new ArrayList<>());

        String sql = new DB2SqlBuilder().buildCreateTable(table, null);

        assertTrue(sql.startsWith("CREATE TABLE \"S\".\"evil\"\" , \"\"x\" "), sql);
    }

    @Test
    void indexCommentNeutralizesMaliciousNameAndComment() {
        TableIndex index = new TableIndex();
        index.setName("ix\"name");
        index.setSchemaName("S");
        index.setTableName("T");
        index.setComment("'; DROP TABLE t; --");

        String sql = DB2IndexTypeEnum.NORMAL.buildIndexComment(index);

        assertEquals("COMMENT ON INDEX \"ix\"\"name\" IS '''; DROP TABLE t; --';", sql);
    }

    @Test
    void getMetaDataNameDoublesEmbeddedQuotes() {
        assertEquals("\"a\".\"b\"\"c\"", new DB2MetaData().getMetaDataName("a", "b\"c"));
    }

    @Test
    void createColumnEscapesNameAndKeepsPlainDefault() {
        TableColumn column = new TableColumn();
        column.setName("c\"x");
        column.setColumnType("INT");
        column.setDefaultValue("0");

        String sql = DB2ColumnTypeEnum.INT.buildCreateColumnSql(column);

        assertTrue(sql.startsWith("\"c\"\"x\" INT DEFAULT 0"), sql);
    }

    @Test
    void createColumnRejectsMaliciousDefaultValue() {
        TableColumn column = new TableColumn();
        column.setName("c");
        column.setColumnType("INT");
        column.setDefaultValue("0; DROP TABLE t");

        assertThrows(IllegalArgumentException.class, () -> DB2ColumnTypeEnum.INT.buildCreateColumnSql(column));
    }

    @Test
    void createColumnRejectsMaliciousUnit() {
        TableColumn column = new TableColumn();
        column.setName("c");
        column.setColumnType("VARCHAR");
        column.setColumnSize(10);
        column.setUnit("OCTETS) DROP TABLE t");

        assertThrows(IllegalArgumentException.class, () -> DB2ColumnTypeEnum.VARCHAR.buildCreateColumnSql(column));
    }
}
