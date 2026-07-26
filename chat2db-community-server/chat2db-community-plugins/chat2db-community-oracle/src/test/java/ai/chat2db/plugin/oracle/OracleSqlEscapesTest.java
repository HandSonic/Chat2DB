package ai.chat2db.plugin.oracle;

import ai.chat2db.community.domain.api.model.metadata.Table;
import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.metadata.TableIndex;
import ai.chat2db.community.domain.api.model.metadata.TableIndexColumn;
import ai.chat2db.community.domain.api.model.value.SQLDataValue;
import ai.chat2db.community.domain.api.model.view.ModifyView;
import ai.chat2db.plugin.oracle.builder.OracleSqlBuilder;
import ai.chat2db.plugin.oracle.enums.type.OracleColumnTypeEnum;
import ai.chat2db.plugin.oracle.enums.type.OracleIndexTypeEnum;
import ai.chat2db.plugin.oracle.value.sub.OracleRawValueProcessor;
import ai.chat2db.plugin.oracle.value.template.OracleDmlValueTemplate;
import com.google.common.io.BaseEncoding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OracleSqlEscapesTest {

    @Test
    void escapeSqlLiteralDoublesSingleQuotes() {
        assertEquals("O''Brien", OracleSqlEscapes.escapeSqlLiteral("O'Brien"));
        assertEquals("", OracleSqlEscapes.escapeSqlLiteral(null));
        assertEquals("plain", OracleSqlEscapes.escapeSqlLiteral("plain"));
    }

    @Test
    void escapeIdentifierDoublesDoubleQuotesAndStripsWrappingQuotes() {
        assertEquals("WE\"\"IRD", OracleSqlEscapes.escapeIdentifier("WE\"IRD"));
        assertEquals("ALREADY", OracleSqlEscapes.escapeIdentifier("\"ALREADY\""));
        assertEquals("\"A\"\"B\"", OracleSqlEscapes.quoteIdentifier("A\"B"));
        assertEquals("", OracleSqlEscapes.escapeIdentifier(null));
    }

    @Test
    void getMetaDataNameNeutralizesEmbeddedQuotes() {
        OracleMetaData metaData = new OracleMetaData();
        String result = metaData.getMetaDataName("PUBLIC", "A\".\"B");
        assertEquals("\"PUBLIC\".\"A\"\".\"\"B\"", result);
        assertFalse(result.contains("A\".\"B\"."), "injection payload must not break out of the quoted identifier");
    }

    @Test
    void dropTableQuotesAndEscapesTableName() {
        String sql = new OracleDBManager().dropTable(null, null, null, "T\"; DROP TABLE U; --");
        assertEquals("DROP TABLE \"T\"\"; DROP TABLE U; --\"", sql);
    }

    @Test
    void buildCreateViewEscapesNamesAndComment() {
        ModifyView view = new ModifyView();
        view.setSchemaName("SA\"LES");
        view.setViewName("V\"; DROP TABLE U; --");
        view.setViewBody("SELECT ID FROM EMPLOYEE");
        view.setComment("x'); DROP TABLE USERS; --");

        String sql = new OracleSqlBuilder().buildCreateView(view);

        assertTrue(sql.startsWith("CREATE VIEW \"SA\"\"LES\".\"V\"\"; DROP TABLE U; --\""));
        assertTrue(sql.endsWith("COMMENT ON TABLE \"SA\"\"LES\".\"V\"\"; DROP TABLE U; --\" is 'x''); DROP TABLE USERS; --';"));
    }

    @Test
    void buildCreateTableEscapesNamesAndComments() {
        Table table = new Table();
        table.setSchemaName("S\"CHEMA");
        table.setName("T\"ABLE");
        table.setComment("x'); DROP TABLE USERS; --");
        TableColumn column = new TableColumn();
        column.setName("C\"OL");
        column.setSchemaName("S\"CHEMA");
        column.setTableName("T\"ABLE");
        column.setColumnType("VARCHAR2");
        column.setColumnSize(20);
        column.setComment("c'); DROP TABLE U; --");
        table.setColumnList(List.of(column));
        table.setIndexList(List.of());

        String sql = new OracleSqlBuilder().buildCreateTable(table, null);

        assertTrue(sql.startsWith("CREATE TABLE \"S\"\"CHEMA\".\"T\"\"ABLE\"("), sql);
        assertTrue(sql.contains("COMMENT ON COLUMN \"S\"\"CHEMA\".\"T\"\"ABLE\".\"C\"\"OL\" IS 'c''); DROP TABLE U; --'"), sql);
        assertTrue(sql.contains("COMMENT ON TABLE \"S\"\"CHEMA\".\"T\"\"ABLE\" IS 'x''); DROP TABLE USERS; --'"), sql);
    }

    @Test
    void buildModifyColumnDeleteEscapesQualifiedNames() {
        TableColumn column = new TableColumn();
        column.setEditStatus("DELETE");
        column.setSchemaName("S\"; DROP TABLE U; --");
        column.setTableName("T");
        column.setName("C");

        String sql = OracleColumnTypeEnum.VARCHAR2.buildModifyColumn(column);

        assertEquals("ALTER TABLE \"S\"\"; DROP TABLE U; --\".\"T\" DROP COLUMN \"C\"", sql);
    }

    @Test
    void buildIndexScriptEscapesNamesAndValidatesDirection() {
        TableIndex index = new TableIndex();
        index.setType(OracleIndexTypeEnum.NORMAL.getName());
        index.setSchemaName("S\"; X");
        index.setTableName("T");
        index.setName("I\"X");
        TableIndexColumn indexColumn = new TableIndexColumn();
        indexColumn.setColumnName("C\"D");
        indexColumn.setAscOrDesc("desc");
        index.setColumnList(List.of(indexColumn));

        String sql = OracleIndexTypeEnum.NORMAL.buildIndexScript(index);

        assertEquals("CREATE INDEX \"S\"\"; X\".\"I\"\"X\" ON \"S\"\"; X\".\"T\" (\"C\"\"D\" DESC)", sql);
    }

    @Test
    void buildIndexColumnRejectsHostileSortDirection() {
        TableIndex index = new TableIndex();
        index.setType(OracleIndexTypeEnum.NORMAL.getName());
        index.setSchemaName("S");
        index.setTableName("T");
        index.setName("I");
        TableIndexColumn indexColumn = new TableIndexColumn();
        indexColumn.setColumnName("C");
        indexColumn.setAscOrDesc("DESC; DROP TABLE U; --");
        index.setColumnList(List.of(indexColumn));

        assertThrows(IllegalArgumentException.class, () -> OracleIndexTypeEnum.NORMAL.buildIndexScript(index));
    }

    @Test
    void buildCreateColumnSqlAcceptsLegitimateDefaults() {
        String[] valid = {"SYSDATE", "CURRENT_TIMESTAMP", "USER", "SEQ.NEXTVAL", "-1", "1.5",
                "'Y'", "'0'", "'O''Brien'", "'1970-01-01'", "''"};
        for (String defaultValue : valid) {
            TableColumn column = new TableColumn();
            column.setName("c1");
            column.setColumnType("VARCHAR2");
            column.setDefaultValue(defaultValue);
            assertTrue(OracleColumnTypeEnum.VARCHAR2.buildCreateColumnSql(column).contains("DEFAULT " + defaultValue),
                    "default should be accepted: " + defaultValue);
        }
    }

    @Test
    void buildCreateColumnSqlRejectsDefaultValuesThatReshapeDdl() {
        String[] payloads = {
                "0) --",
                "0 --",
                "1, x INT",
                "now()",
                "0); DROP TABLE x--",
                "'abc",
                "'a' 'b'",
                "'a'||'b'",
                "'a'--",
                "'a'; DROP TABLE x--"
        };
        for (String payload : payloads) {
            TableColumn column = new TableColumn();
            column.setName("c1");
            column.setColumnType("VARCHAR2");
            column.setDefaultValue(payload);
            assertThrows(IllegalArgumentException.class,
                    () -> OracleColumnTypeEnum.VARCHAR2.buildCreateColumnSql(column),
                    "default should be rejected: " + payload);
        }
    }

    @Test
    void buildCreateColumnSqlAcceptsUnknownButSafeTypeNames() {
        String[] valid = {"MYCUSTOMTYPE", "VARCHAR2(20)", "NUMBER(10,2)", "TIMESTAMP(6) WITH TIME ZONE",
                "INTERVAL DAY(2) TO SECOND(6)", "VARCHAR2(20 CHAR)"};
        for (String typeName : valid) {
            TableColumn column = new TableColumn();
            column.setName("c1");
            column.setColumnType(typeName);
            assertEquals("c1 " + typeName, OracleColumnTypeEnum.VARCHAR2.buildCreateColumnSql(column),
                    "type should be accepted: " + typeName);
        }
    }

    @Test
    void buildCreateColumnSqlRejectsHostileTypeNames() {
        String[] payloads = {"INTEGER); DROP TABLE U; --", "INT, x INT", "INT'--", "INT\"--", "0) --"};
        for (String payload : payloads) {
            TableColumn column = new TableColumn();
            column.setName("c1");
            column.setColumnType(payload);
            assertThrows(IllegalArgumentException.class,
                    () -> OracleColumnTypeEnum.VARCHAR2.buildCreateColumnSql(column),
                    "type should be rejected: " + payload);
        }
    }

    @Test
    void requireUnitAcceptsCharAndByteOnly() {
        assertEquals("CHAR", OracleSqlEscapes.requireUnit("CHAR"));
        assertEquals("byte", OracleSqlEscapes.requireUnit("byte"));
        assertThrows(IllegalArgumentException.class, () -> OracleSqlEscapes.requireUnit("BYTE); DROP TABLE U; --"));
    }

    @Test
    void requireAscOrDescCanonicalizesAndRejects() {
        assertEquals("ASC", OracleSqlEscapes.requireAscOrDesc("asc"));
        assertEquals("DESC", OracleSqlEscapes.requireAscOrDesc(" DESC "));
        assertThrows(IllegalArgumentException.class, () -> OracleSqlEscapes.requireAscOrDesc("DESC; DROP TABLE U; --"));
    }

    @Test
    void wrapDateEscapesSingleQuotesInLiteralPosition() {
        String sql = OracleDmlValueTemplate.wrapDate("2020-01-01' OR '1'='1");
        assertEquals("TO_DATE('2020-01-01'' OR ''1''=''1', 'SYYYY-MM-DD HH24:MI:SS')", sql);
        assertFalse(sql.contains("'2020-01-01' OR"));
    }

    @Test
    void rawProcessorNeutralizesNonHexAfterZeroXPrefix() {
        SQLDataValue dataValue = new SQLDataValue();
        dataValue.setValue("0x'; DROP TABLE U; --");

        String sql = new OracleRawValueProcessor().convertSQLValueByType(dataValue);

        String expectedHex = BaseEncoding.base16().encode("0x'; DROP TABLE U; --".getBytes());
        assertEquals("'" + expectedHex + "'", sql);
    }

    @Test
    void rawProcessorKeepsValidHexLiteral() {
        SQLDataValue dataValue = new SQLDataValue();
        dataValue.setValue("0x1A2b");

        String sql = new OracleRawValueProcessor().convertSQLValueByType(dataValue);

        assertEquals("'1A2b'", sql);
    }
}
