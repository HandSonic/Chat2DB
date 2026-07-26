package ai.chat2db.plugin.oceanbase.oracle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OceanbaseOracleSqlEscapesTest {

    @Test
    void escapeSqlLiteralDoublesSingleQuotes() {
        assertEquals("O''Brien", OceanbaseOracleSqlEscapes.escapeSqlLiteral("O'Brien"));
        assertEquals("plain", OceanbaseOracleSqlEscapes.escapeSqlLiteral("plain"));
        assertEquals("", OceanbaseOracleSqlEscapes.escapeSqlLiteral(null));
    }

    @Test
    void quoteIdentifierDoublesEmbeddedDoubleQuotes() {
        assertEquals("\"MY_TABLE\"", OceanbaseOracleSqlEscapes.quoteIdentifier("MY_TABLE"));
        assertEquals("\"we\"\"ird\"", OceanbaseOracleSqlEscapes.quoteIdentifier("we\"ird"));
        assertEquals("\"we\"\"ird\"", OceanbaseOracleSqlEscapes.quoteIdentifier("\"we\"ird\""));
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
