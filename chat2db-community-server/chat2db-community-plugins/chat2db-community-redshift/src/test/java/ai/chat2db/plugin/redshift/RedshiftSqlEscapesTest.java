package ai.chat2db.plugin.redshift;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedshiftSqlEscapesTest {

    @Test
    void escapeSqlLiteralDoublesSingleQuotes() {
        assertEquals("o''brien", RedshiftSqlEscapes.escapeSqlLiteral("o'brien"));
        assertEquals("plain", RedshiftSqlEscapes.escapeSqlLiteral("plain"));
        assertEquals("''''", RedshiftSqlEscapes.escapeSqlLiteral("''"));
        assertNull(RedshiftSqlEscapes.escapeSqlLiteral(null));
    }

    @Test
    void escapeIdentifierDoublesEmbeddedDoubleQuotes() {
        assertEquals("we\"\"ird", RedshiftSqlEscapes.escapeIdentifier("we\"ird"));
        assertEquals("plain", RedshiftSqlEscapes.escapeIdentifier("plain"));
        assertNull(RedshiftSqlEscapes.escapeIdentifier(null));
    }

    @Test
    void escapeIdentifierStripsOneSurroundingQuotePair() {
        assertEquals("foo", RedshiftSqlEscapes.escapeIdentifier("\"foo\""));
        assertEquals("fo\"\"o", RedshiftSqlEscapes.escapeIdentifier("\"fo\"o\""));
    }

    @Test
    void quoteIdentifierWrapsAndEscapes() {
        assertEquals("\"foo\"", RedshiftSqlEscapes.quoteIdentifier("foo"));
        assertEquals("\"foo\"", RedshiftSqlEscapes.quoteIdentifier("\"foo\""));
        assertEquals("\"we\"\"ird\"", RedshiftSqlEscapes.quoteIdentifier("we\"ird"));
        assertEquals("", RedshiftSqlEscapes.quoteIdentifier(""));
        assertNull(RedshiftSqlEscapes.quoteIdentifier(null));
    }

    @Test
    void maliciousSchemaNameIsNeutralizedInShowCreateTable() {
        String sql = RedshiftMetaData.buildShowCreateTableSql("public\"; DROP TABLE users; --", "t");
        assertEquals("SHOW CREATE TABLE \"public\"\"; DROP TABLE users; --\".\"t\"", sql);
    }

    @Test
    void maliciousTableNameIsNeutralizedInShowCreateTable() {
        String sql = RedshiftMetaData.buildShowCreateTableSql("public", "t\" OR \"1\"=\"1");
        assertEquals("SHOW CREATE TABLE \"public\".\"t\"\" OR \"\"1\"\"=\"\"1\"", sql);
        assertTrue(sql.startsWith("SHOW CREATE TABLE \"public\"."));
    }

    @Test
    void benignNamesProduceSameSqlAsBefore() {
        assertEquals("SHOW CREATE TABLE \"public\".\"orders\"",
                RedshiftMetaData.buildShowCreateTableSql("public", "orders"));
        assertEquals("SHOW CREATE TABLE \"public\".\"orders\"",
                RedshiftMetaData.buildShowCreateTableSql("\"public\"", "\"orders\""));
    }
}
