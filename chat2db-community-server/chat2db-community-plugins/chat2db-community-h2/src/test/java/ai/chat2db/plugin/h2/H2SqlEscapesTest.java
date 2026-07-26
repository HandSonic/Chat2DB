package ai.chat2db.plugin.h2;

import ai.chat2db.community.domain.api.model.metadata.Schema;
import ai.chat2db.plugin.h2.builder.H2SqlBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void escapeIdentifierHandlesNullAndQuoteOnlyInput() {
        assertEquals("", H2SqlEscapes.escapeIdentifier(null));
        assertEquals("\"\"", H2SqlEscapes.quoteIdentifier(null));
        assertEquals("\"\"\"\"", H2SqlEscapes.quoteIdentifier("\""));
        assertEquals("", H2SqlEscapes.escapeIdentifier("\"\""));
    }

    @Test
    void requireSafeTypeNameAcceptsRealH2TypesAndRejectsInjection() {
        assertEquals("INTEGER", H2SqlEscapes.requireSafeTypeName("INTEGER"));
        assertEquals("CHARACTER VARYING", H2SqlEscapes.requireSafeTypeName("CHARACTER VARYING"));
        assertEquals("DOUBLE PRECISION", H2SqlEscapes.requireSafeTypeName("DOUBLE PRECISION"));
        assertThrows(IllegalArgumentException.class,
            () -> H2SqlEscapes.requireSafeTypeName("INT; DROP TABLE USERS; --"));
        assertThrows(IllegalArgumentException.class,
            () -> H2SqlEscapes.requireSafeTypeName("INT')"));
        assertNull(H2SqlEscapes.requireSafeTypeName(null));
    }

    @Test
    void escapeColumnDefaultKeepsWellFormedLiteralsAndExpressions() {
        assertEquals("'O''Brien'", H2SqlEscapes.escapeColumnDefault("'O''Brien'"));
        assertEquals("CURRENT_TIMESTAMP", H2SqlEscapes.escapeColumnDefault("CURRENT_TIMESTAMP"));
        assertEquals("42", H2SqlEscapes.escapeColumnDefault("42"));
        assertEquals("-1", H2SqlEscapes.escapeColumnDefault("-1"));
        assertEquals("NEXT VALUE FOR SEQ1", H2SqlEscapes.escapeColumnDefault("NEXT VALUE FOR SEQ1"));
        assertEquals("", H2SqlEscapes.escapeColumnDefault(null));
    }

    @Test
    void escapeColumnDefaultNeutralizesAttackStrings() {
        assertEquals("'x''); DROP TABLE USERS; --'",
            H2SqlEscapes.escapeColumnDefault("'x'); DROP TABLE USERS; --'"));
        assertEquals("'0; DROP TABLE USERS; --'",
            H2SqlEscapes.escapeColumnDefault("0; DROP TABLE USERS; --"));
        assertEquals("'||'", H2SqlEscapes.escapeColumnDefault("'||'"));
    }
}
