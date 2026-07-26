package ai.chat2db.plugin.oscar;

import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.metadata.TableIndex;
import ai.chat2db.community.domain.api.model.metadata.TableIndexColumn;
import ai.chat2db.plugin.oscar.constant.OscarConstants;
import ai.chat2db.plugin.oscar.enums.type.OscarColumnTypeEnum;
import ai.chat2db.plugin.oscar.enums.type.OscarIndexTypeEnum;
import ai.chat2db.plugin.oscar.util.OscarUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OscarSqlEscapesTest {

    @Test
    void escapeSqlLiteralDoublesSingleQuotes() {
        assertEquals("O''Brien", OscarSqlEscapes.escapeSqlLiteral("O'Brien"));
        assertEquals("plain", OscarSqlEscapes.escapeSqlLiteral("plain"));
        assertNull(OscarSqlEscapes.escapeSqlLiteral(null));
    }

    @Test
    void quoteIdentifierDoublesEmbeddedDoubleQuotes() {
        assertEquals("\"plain\"", OscarSqlEscapes.quoteIdentifier("plain"));
        assertEquals("\"MyTable\"", OscarSqlEscapes.quoteIdentifier("\"MyTable\""));
        assertEquals("\"a\"\"b\"", OscarSqlEscapes.quoteIdentifier("a\"b"));
        assertEquals("\"x\"\"; DROP TABLE t; --\"",
                OscarSqlEscapes.quoteIdentifier("x\"; DROP TABLE t; --"));
    }

    @Test
    void identifierProcessorNeutralizesMaliciousNames() {
        assertEquals("\"a\"\"b\"", OscarUtils.quoteIdentifierIgnoreCase("a\"b"));
        assertEquals("\"x\"\"; DROP TABLE t; --\"",
                OscarUtils.quoteIdentifierIgnoreCase("x\"; DROP TABLE t; --"));
        assertEquals("SYSDBA", OscarUtils.quoteIdentifierIgnoreCase("SYSDBA"));
        assertEquals("\"MyTable\"", OscarUtils.quoteIdentifierIgnoreCase("\"MyTable\""));
    }

    @Test
    void metadataSqlTemplatesNeutralizeMaliciousLiterals() {
        String malicious = "X' OR '1'='1";
        String sql = String.format(OscarConstants.VIEW_DDL_SQL,
                OscarSqlEscapes.escapeSqlLiteral(malicious),
                OscarSqlEscapes.escapeSqlLiteral(malicious));
        assertTrue(sql.contains("OWNER = 'X'' OR ''1''=''1'"));
        assertTrue(sql.contains("VIEW_NAME = 'X'' OR ''1''=''1'"));

        String triggerSql = String.format(OscarConstants.TRIGGER_DETAIL_SQL,
                OscarSqlEscapes.escapeSqlLiteral("SYSDBA"),
                OscarSqlEscapes.escapeSqlLiteral(malicious));
        assertTrue(triggerSql.contains("TRIGGER_NAME = 'X'' OR ''1''=''1'"));
    }

    @Test
    void createColumnSqlNeutralizesMaliciousColumnName() {
        TableColumn column = new TableColumn();
        column.setName("col\"x");
        column.setColumnType("VARCHAR");
        column.setColumnSize(10);
        String sql = OscarColumnTypeEnum.VARCHAR.buildCreateColumnSql(column);
        assertTrue(sql.startsWith("\"col\"\"x\" VARCHAR(10)"));
    }

    @Test
    void createIndexSqlNeutralizesMaliciousIndexName() {
        TableIndex index = new TableIndex();
        index.setName("idx\"; DROP TABLE t; --");
        index.setType(OscarIndexTypeEnum.NORMAL.getName());
        index.setTableName("T1");
        TableIndexColumn indexColumn = new TableIndexColumn();
        indexColumn.setColumnName("C1");
        indexColumn.setAscOrDesc("ASC");
        index.setColumnList(List.of(indexColumn));
        String sql = OscarIndexTypeEnum.NORMAL.buildIndexScript(index);
        assertTrue(sql.contains("\"idx\"\"; DROP TABLE t; --\""));
        assertTrue(sql.contains("(C1 ASC)"));
    }

    @Test
    void defaultValueWhitelistAcceptsLegitimateValues() {
        assertEquals("0", OscarSqlEscapes.requireDefaultValueExpression("0"));
        assertEquals("-1", OscarSqlEscapes.requireDefaultValueExpression("-1"));
        assertEquals("3.14", OscarSqlEscapes.requireDefaultValueExpression("3.14"));
        assertEquals("1e10", OscarSqlEscapes.requireDefaultValueExpression("1e10"));
        assertEquals("SYSDATE", OscarSqlEscapes.requireDefaultValueExpression("SYSDATE"));
        assertEquals("CURRENT_TIMESTAMP", OscarSqlEscapes.requireDefaultValueExpression("CURRENT_TIMESTAMP"));
        assertEquals("sys_guid()", OscarSqlEscapes.requireDefaultValueExpression("sys_guid()"));
        assertEquals("to_date('2024-01-01', 'YYYY-MM-DD')",
                OscarSqlEscapes.requireDefaultValueExpression("to_date('2024-01-01', 'YYYY-MM-DD')"));
    }

    @Test
    void defaultValueWhitelistAcceptsQuotedStringDefaults() {
        assertEquals("'abc'", OscarSqlEscapes.requireDefaultValueExpression("'abc'"));
        assertEquals("'O''Brien'", OscarSqlEscapes.requireDefaultValueExpression("'O''Brien'"));
        assertEquals("''", OscarSqlEscapes.requireDefaultValueExpression("''"));
    }

    @Test
    void defaultValueWhitelistRejectsInjection() {
        assertThrows(IllegalArgumentException.class,
                () -> OscarSqlEscapes.requireDefaultValueExpression("'; DROP TABLE t; --"));
        assertThrows(IllegalArgumentException.class,
                () -> OscarSqlEscapes.requireDefaultValueExpression("1; DROP TABLE t"));
        assertThrows(IllegalArgumentException.class,
                () -> OscarSqlEscapes.requireDefaultValueExpression("a' OR '1'='1"));
        assertThrows(IllegalArgumentException.class,
                () -> OscarSqlEscapes.requireDefaultValueExpression("f(''); DROP TABLE t; --('x')"));
    }

    @Test
    void createColumnSqlRejectsMaliciousDefaultValue() {
        TableColumn column = new TableColumn();
        column.setName("C1");
        column.setColumnType("VARCHAR");
        column.setColumnSize(10);
        column.setDefaultValue("'; DROP TABLE t; --");
        assertThrows(IllegalArgumentException.class,
                () -> OscarColumnTypeEnum.VARCHAR.buildCreateColumnSql(column));
    }

    @Test
    void createColumnSqlKeepsQuotedStringDefault() {
        TableColumn column = new TableColumn();
        column.setName("C1");
        column.setColumnType("VARCHAR");
        column.setColumnSize(10);
        column.setDefaultValue("'O''Brien'");
        String sql = OscarColumnTypeEnum.VARCHAR.buildCreateColumnSql(column);
        assertTrue(sql.contains("DEFAULT 'O''Brien'"));
    }

    @Test
    void lengthUnitWhitelistAcceptsByteAndChar() {
        assertEquals("BYTE", OscarSqlEscapes.requireLengthUnit("BYTE"));
        assertEquals("char", OscarSqlEscapes.requireLengthUnit("char"));
        assertThrows(IllegalArgumentException.class,
                () -> OscarSqlEscapes.requireLengthUnit("BYTE; DROP TABLE t"));
    }

    @Test
    void sortOrderWhitelistAcceptsAscDesc() {
        assertEquals("ASC", OscarSqlEscapes.requireSortOrder("ASC"));
        assertEquals("desc", OscarSqlEscapes.requireSortOrder("desc"));
        assertThrows(IllegalArgumentException.class,
                () -> OscarSqlEscapes.requireSortOrder("ASC; DROP TABLE t; --"));
    }

    @Test
    void createIndexSqlRejectsMaliciousSortOrder() {
        TableIndex index = new TableIndex();
        index.setName("IDX1");
        index.setType(OscarIndexTypeEnum.NORMAL.getName());
        index.setTableName("T1");
        TableIndexColumn indexColumn = new TableIndexColumn();
        indexColumn.setColumnName("C1");
        indexColumn.setAscOrDesc("ASC; DROP TABLE t; --");
        index.setColumnList(List.of(indexColumn));
        assertThrows(IllegalArgumentException.class,
                () -> OscarIndexTypeEnum.NORMAL.buildIndexScript(index));
    }
}
