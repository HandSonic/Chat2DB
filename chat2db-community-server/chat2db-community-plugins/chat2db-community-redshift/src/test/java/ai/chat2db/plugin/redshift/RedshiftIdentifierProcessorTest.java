package ai.chat2db.plugin.redshift;

import ai.chat2db.plugin.redshift.identifier.RedshiftIdentifierProcessor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedshiftIdentifierProcessorTest {

    @Test
    void escapeSqlLiteralDoublesSingleQuotes() {
        assertEquals("o''brien", RedshiftIdentifierProcessor.INSTANCE.escapeString("o'brien"));
        assertEquals("plain", RedshiftIdentifierProcessor.INSTANCE.escapeString("plain"));
        assertEquals("''''", RedshiftIdentifierProcessor.INSTANCE.escapeString("''"));
        assertNull(RedshiftIdentifierProcessor.INSTANCE.escapeString(null));
    }

    @Test
    void escapeIdentifierDoublesEmbeddedDoubleQuotes() {
        assertEquals("we\"\"ird", RedshiftIdentifierProcessor.escapeIdentifier("we\"ird"));
        assertEquals("plain", RedshiftIdentifierProcessor.escapeIdentifier("plain"));
        assertNull(RedshiftIdentifierProcessor.escapeIdentifier(null));
    }

    @Test
    void escapeIdentifierStripsOneSurroundingQuotePair() {
        assertEquals("foo", RedshiftIdentifierProcessor.escapeIdentifier("\"foo\""));
        assertEquals("fo\"\"o", RedshiftIdentifierProcessor.escapeIdentifier("\"fo\"o\""));
    }

    @Test
    void quoteIdentifierWrapsAndEscapes() {
        assertEquals("\"foo\"", RedshiftIdentifierProcessor.INSTANCE.quoteIdentifier("foo"));
        assertEquals("\"foo\"", RedshiftIdentifierProcessor.INSTANCE.quoteIdentifier("\"foo\""));
        assertEquals("\"we\"\"ird\"", RedshiftIdentifierProcessor.INSTANCE.quoteIdentifier("we\"ird"));
        assertEquals("", RedshiftIdentifierProcessor.INSTANCE.quoteIdentifier(""));
        assertNull(RedshiftIdentifierProcessor.INSTANCE.quoteIdentifier(null));
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
