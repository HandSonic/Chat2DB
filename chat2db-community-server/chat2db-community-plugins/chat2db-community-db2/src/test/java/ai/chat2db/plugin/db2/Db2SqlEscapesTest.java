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

    @Test
    void fallbackColumnContainsMaliciousNameInsideQuotedIdentifier() {
        TableColumn column = new TableColumn();
        column.setName("x INT); DROP TABLE t; --");
        column.setColumnType("VARCHAR(10)");

        String sql = DB2ColumnTypeEnum.VARCHAR.buildCreateColumnSql(column);

        assertEquals("\"x INT); DROP TABLE t; --\" VARCHAR(10)", sql);
    }

    @Test
    void fallbackColumnEscapesEmbeddedQuoteInName() {
        TableColumn column = new TableColumn();
        column.setName("a\"b");
        column.setColumnType("VARCHAR(10)");

        String sql = DB2ColumnTypeEnum.VARCHAR.buildCreateColumnSql(column);

        assertEquals("\"a\"\"b\" VARCHAR(10)", sql);
    }

    @Test
    void fallbackColumnRejectsMaliciousColumnType() {
        TableColumn column = new TableColumn();
        column.setName("c");
        column.setColumnType("INT); DROP TABLE t; --");

        assertThrows(IllegalArgumentException.class, () -> DB2ColumnTypeEnum.INT.buildCreateColumnSql(column));

        column.setColumnType("VARCHAR(10); DROP TABLE t");
        assertThrows(IllegalArgumentException.class, () -> DB2ColumnTypeEnum.VARCHAR.buildCreateColumnSql(column));

        column.setColumnType("VAR CHAR");
        assertThrows(IllegalArgumentException.class, () -> DB2ColumnTypeEnum.VARCHAR.buildCreateColumnSql(column));
    }

    @Test
    void tableDDLRejectsQuoteBearingNames() {
        DB2MetaData metaData = new DB2MetaData();

        assertThrows(IllegalArgumentException.class, () -> metaData.tableDDL(null, null, "s\"x", "t"));
        assertThrows(IllegalArgumentException.class, () -> metaData.tableDDL(null, null, "s", "x\" -t \"y"));
    }

    @Test
    void setSchemaEscapesIdentifierInsideTemplateQuotes() {
        String sql = String.format(ai.chat2db.plugin.db2.constant.DB2DBManagerConstants.SQL_SET_SCHEMA,
                Db2SqlEscapes.escapeIdentifier("bad\"schema"));

        assertEquals("SET SCHEMA \"bad\"\"schema\"", sql);
    }

    @Test
    void dropTableQuotesIdentifier() {
        String sql = new DB2DBManager().dropTable(null, null, null, "t\"x");

        assertEquals("DROP TABLE \"t\"\"x\"", sql);
    }

    @Test
    void copyTableQuotesBothIdentifiers() {
        String sql = String.format(ai.chat2db.plugin.db2.constant.DB2DBManagerConstants.SQL_COPY_TABLE,
                Db2SqlEscapes.quoteIdentifier("n\"t"), Db2SqlEscapes.quoteIdentifier("s\"t"));

        assertEquals("CREATE TABLE \"n\"\"t\" LIKE \"s\"\"t\" INCLUDING INDEXES", sql);
    }
}
