package ai.chat2db.plugin.sqlserver.value;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlServerValueProcessorTest {

    private final SqlServerValueProcessor processor = new SqlServerValueProcessor();

    @Test
    void isStringDataTypeCoversUnicodeAndPlainStringTypes() {
        assertTrue(processor.isStringDataType("CHAR"));
        assertTrue(processor.isStringDataType("VARCHAR"));
        assertTrue(processor.isStringDataType("TEXT"));
        assertTrue(processor.isStringDataType("NCHAR"));
        assertTrue(processor.isStringDataType("NVARCHAR"));
        assertTrue(processor.isStringDataType("NTEXT"));
    }

    @Test
    void isStringDataTypeIsCaseInsensitive() {
        assertTrue(processor.isStringDataType("nvarchar"));
        assertTrue(processor.isStringDataType("Nchar"));
    }

    @Test
    void isStringDataTypeRejectsNonStringTypes() {
        assertFalse(processor.isStringDataType("INT"));
        assertFalse(processor.isStringDataType("DATETIME"));
        assertFalse(processor.isStringDataType(null));
    }
}
