package ai.chat2db.plugin.h2;

import ai.chat2db.community.domain.api.model.metadata.Schema;
import ai.chat2db.plugin.h2.builder.H2SqlBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class H2SqlEscapesTest {

    @Test
    void escapeSqlLiteralDoublesSingleQuotes() {
        assertEquals("O''Brien", H2SqlEscapes.escapeSqlLiteral("O'Brien"));
        assertEquals("", H2SqlEscapes.escapeSqlLiteral(null));
        assertEquals("plain", H2SqlEscapes.escapeSqlLiteral("plain"));
    }

    @Test
    void escapeIdentifierDoublesDoubleQuotesAndStripsWrappingQuotes() {
        assertEquals("WE\"\"IRD", H2SqlEscapes.escapeIdentifier("WE\"IRD"));
        assertEquals("ALREADY", H2SqlEscapes.escapeIdentifier("\"ALREADY\""));
        assertEquals("\"A\"\"B\"", H2SqlEscapes.quoteIdentifier("A\"B"));
    }

    @Test
    void getMetaDataNameNeutralizesEmbeddedQuotes() {
        H2Meta meta = new H2Meta();
        String result = meta.getMetaDataName("PUBLIC", "A\".\"B");
        assertEquals("\"PUBLIC\".\"A\"\".\"\"B\"", result);
        assertFalse(result.contains("A\".\"B\"."), "injection payload must not break out of the quoted identifier");
    }

    @Test
    void buildCreateSchemaEscapesNameAndComment() {
        Schema schema = new Schema();
        schema.setName("EVIL\" SCHEMA");
        schema.setComment("x'); DROP TABLE USERS; --");
        String sql = new H2SqlBuilder().buildCreateSchema(schema);
        assertEquals("CREATE SCHEMA \"EVIL\"\" SCHEMA\";\n"
            + "COMMENT ON SCHEMA \"EVIL\"\" SCHEMA\" IS 'x''); DROP TABLE USERS; --';", sql);
    }

    @Test
    void dropTableQuotesAndEscapesTableName() {
        String sql = new H2DBManager().dropTable(null, "TEST", "PUBLIC", "T\"; DROP TABLE U; --");
        assertEquals("DROP TABLE \"T\"\"; DROP TABLE U; --\"", sql);
    }
}
