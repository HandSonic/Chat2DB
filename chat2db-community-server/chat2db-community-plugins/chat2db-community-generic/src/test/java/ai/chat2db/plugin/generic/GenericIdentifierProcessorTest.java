package ai.chat2db.plugin.generic;

import ai.chat2db.plugin.generic.identifier.GenericIdentifierProcessor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GenericIdentifierProcessorTest {

    @Test
    void escapeStringDoublesSingleQuotes() {
        assertNull(GenericIdentifierProcessor.INSTANCE.escapeString(null));
        assertEquals("plain", GenericIdentifierProcessor.INSTANCE.escapeString("plain"));
        assertEquals("a''b", GenericIdentifierProcessor.INSTANCE.escapeString("a'b"));
        assertEquals("''; DROP TABLE x; --",
                GenericIdentifierProcessor.INSTANCE.escapeString("'; DROP TABLE x; --"));
    }

    @Test
    void quoteIdentifierDoublesDialectQuoteChar() {
        // DuckDB-style double quotes
        assertEquals("\"plain\"", GenericIdentifierProcessor.quoteIdentifier("plain", '"'));
        assertEquals("\"a\"\"b\"", GenericIdentifierProcessor.quoteIdentifier("a\"b", '"'));
        assertEquals("\"we\"\"\"\"ird\"", GenericIdentifierProcessor.quoteIdentifier("\"we\"\"ird\"", '"'));
        // TDengine-style backticks
        assertEquals("`a``b`", GenericIdentifierProcessor.quoteIdentifier("a`b", '`'));
        assertEquals("`a``; DROP TABLE b; --`",
                GenericIdentifierProcessor.quoteIdentifier("a`; DROP TABLE b; --", '`'));
    }

    @Test
    void quoteIdentifierDefaultsToDoubleQuotes() {
        assertEquals("\"plain\"", GenericIdentifierProcessor.INSTANCE.quoteIdentifier("plain"));
        assertEquals("\"a\"\"b\"", GenericIdentifierProcessor.INSTANCE.quoteIdentifier("a\"b"));
        assertEquals("\"a\"\"b\"", GenericIdentifierProcessor.INSTANCE.quoteIdentifier("a\"b", null, null));
        assertEquals("\"a\"\"b\"", GenericIdentifierProcessor.INSTANCE.quoteIdentifierIgnoreCase("a\"b"));
    }

    @Test
    void escapeIdentifierStripsSurroundingPairAndDoublesEmbeddedQuotes() {
        assertEquals("a\"\"b", GenericIdentifierProcessor.escapeIdentifier("a\"b"));
        assertEquals("we\"\"\"\"ird", GenericIdentifierProcessor.escapeIdentifier("\"we\"\"ird\""));
        assertEquals("a``b", GenericIdentifierProcessor.escapeIdentifier("a`b", '`'));
        assertEquals("", GenericIdentifierProcessor.escapeIdentifier(null));
    }
}
