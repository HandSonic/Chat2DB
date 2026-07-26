package ai.chat2db.plugin.oceanbase.oracle;

import ai.chat2db.plugin.oceanbase.oracle.identifier.OceanbaseOracleIdentifierProcessor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class OceanbaseOracleIdentifierProcessorTest {

    @Test
    void escapeSqlLiteralDoublesSingleQuotes() {
        assertEquals("O''Brien", OceanbaseOracleIdentifierProcessor.INSTANCE.escapeString("O'Brien"));
        assertEquals("plain", OceanbaseOracleIdentifierProcessor.INSTANCE.escapeString("plain"));
        assertEquals("", OceanbaseOracleIdentifierProcessor.INSTANCE.escapeString(null));
    }

    @Test
    void quoteIdentifierPassesThroughNullAndBlank() {
        assertNull(OceanbaseOracleIdentifierProcessor.INSTANCE.quoteIdentifier(null));
        assertEquals("", OceanbaseOracleIdentifierProcessor.INSTANCE.quoteIdentifier(""));
        assertEquals("  ", OceanbaseOracleIdentifierProcessor.INSTANCE.quoteIdentifier("  "));
    }

    @Test
    void quoteIdentifierLeavesPlainIdentifiersUnquoted() {
        assertEquals("MY_TABLE", OceanbaseOracleIdentifierProcessor.INSTANCE.quoteIdentifier("MY_TABLE"));
        assertEquals("my_table", OceanbaseOracleIdentifierProcessor.INSTANCE.quoteIdentifier("my_table"));
        assertEquals("COL_1", OceanbaseOracleIdentifierProcessor.INSTANCE.quoteIdentifier("COL_1"));
    }

    @Test
    void quoteIdentifierQuotesReservedKeywordsAndSpecialChars() {
        assertEquals("\"TABLE\"", OceanbaseOracleIdentifierProcessor.INSTANCE.quoteIdentifier("TABLE"));
        assertEquals("\"select\"", OceanbaseOracleIdentifierProcessor.INSTANCE.quoteIdentifier("select"));
        assertEquals("\"has space\"", OceanbaseOracleIdentifierProcessor.INSTANCE.quoteIdentifier("has space"));
        assertEquals("\"we\"\"ird\"", OceanbaseOracleIdentifierProcessor.INSTANCE.quoteIdentifier("we\"ird"));
        assertEquals("\"we\"\"ird\"", OceanbaseOracleIdentifierProcessor.INSTANCE.quoteIdentifier("\"we\"ird\""));
    }

    @Test
    void versionedQuoteIdentifierDelegatesToConditional() {
        assertEquals("MY_TABLE", OceanbaseOracleIdentifierProcessor.INSTANCE.quoteIdentifier("MY_TABLE", 4, 2));
        assertEquals("\"TABLE\"", OceanbaseOracleIdentifierProcessor.INSTANCE.quoteIdentifier("TABLE", null, null));
        assertNull(OceanbaseOracleIdentifierProcessor.INSTANCE.quoteIdentifier(null, null, null));
    }

    @Test
    void quoteIdentifierAlwaysQuotesUnconditionally() {
        assertNull(OceanbaseOracleIdentifierProcessor.INSTANCE.quoteIdentifierAlways(null));
        assertEquals("\"MY_TABLE\"", OceanbaseOracleIdentifierProcessor.INSTANCE.quoteIdentifierAlways("MY_TABLE"));
        assertEquals("\"we\"\"ird\"", OceanbaseOracleIdentifierProcessor.INSTANCE.quoteIdentifierAlways("we\"ird"));
        assertEquals("\"we\"\"ird\"", OceanbaseOracleIdentifierProcessor.INSTANCE.quoteIdentifierAlways("\"we\"ird\""));
    }

    @Test
    void quoteIdentifierIgnoreCaseIsTheAlwaysQuoteVariant() {
        assertNull(OceanbaseOracleIdentifierProcessor.INSTANCE.quoteIdentifierIgnoreCase(null));
        assertEquals("\"MY_TABLE\"", OceanbaseOracleIdentifierProcessor.INSTANCE.quoteIdentifierIgnoreCase("MY_TABLE"));
        assertEquals("\"my_table\"", OceanbaseOracleIdentifierProcessor.INSTANCE.quoteIdentifierIgnoreCase("my_table"));
    }

    @Test
    void buildTableDdlSqlNeutralizesMaliciousNames() {
        String sql = OceanbaseOracleMetaData.buildTableDdlSql("T' OR '1'='1", "S' OR '1'='1");

        assertEquals("select dbms_metadata.get_ddl('TABLE','T'' OR ''1''=''1','S'' OR ''1''=''1') as sql from dual",
                sql);
        assertFalse(sql.contains("'T' OR '1'='1'"));
    }

    @Test
    void buildTableCommentSqlNeutralizesMaliciousNames() {
        String sql = OceanbaseOracleMetaData.buildTableCommentSql("SCOTT' OR '1'='1", "O'Brien");

        assertEquals("select owner, table_name, comments from ALL_TAB_COMMENTS where OWNER = 'SCOTT'' OR ''1''=''1'"
                + "  and TABLE_NAME = 'O''Brien'", sql);
    }

    @Test
    void buildTableIndexDdlSqlNeutralizesMaliciousIndexName() {
        String sql = OceanbaseOracleMetaData.buildTableIndexDdlSql("IDX', 'S', 'X", "SCOTT");

        assertEquals(String.format(
                        ai.chat2db.plugin.oceanbase.constant.OceanbaseOracleMetaDataConstants.TABLE_INDEX_DDL_SQL,
                        "IDX'', ''S'', ''X", "SCOTT"),
                sql);
        assertFalse(sql.contains("'IDX', 'S', 'X'"));
    }
}
