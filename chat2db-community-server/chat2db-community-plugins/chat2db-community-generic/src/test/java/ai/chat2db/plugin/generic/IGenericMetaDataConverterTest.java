package ai.chat2db.plugin.generic;

import ai.chat2db.community.domain.api.model.metadata.ColumnType;
import ai.chat2db.community.domain.api.model.metadata.Type;
import org.junit.jupiter.api.Test;

import java.sql.DatabaseMetaData;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the JDBC-metadata fallback used when generic.json declares no columnTypes
 * (ELASTICSEARCH, TDENGINE): MapStruct previously mapped only typeName, leaving every supportXxx
 * flag false, which made the table editor emit DDL without NULL/DEFAULT/length clauses.
 */
class IGenericMetaDataConverterTest {

    @Test
    void derivesSupportFlagsFromTypeInfo() {
        Type type = Type.builder()
                .typeName("INT")
                .dataType(java.sql.Types.INTEGER)
                .precision(10)
                .maximumScale((short) 0)
                .nullable((short) DatabaseMetaData.typeNullable)
                .autoIncrement(Boolean.TRUE)
                .build();

        ColumnType columnType = IGenericMetaDataConverter.INSTANCE.type2columnType(type);

        assertEquals("INT", columnType.getTypeName());
        assertTrue(columnType.isSupportLength());
        assertFalse(columnType.isSupportScale());
        assertTrue(columnType.isSupportNullable());
        assertTrue(columnType.isSupportAutoIncrement());
    }

    @Test
    void decimalTypeSupportsScale() {
        Type type = Type.builder()
                .typeName("DECIMAL")
                .dataType(java.sql.Types.DECIMAL)
                .precision(38)
                .maximumScale((short) 18)
                .nullable((short) DatabaseMetaData.typeNullable)
                .autoIncrement(Boolean.FALSE)
                .build();

        ColumnType columnType = IGenericMetaDataConverter.INSTANCE.type2columnType(type);

        assertTrue(columnType.isSupportLength());
        assertTrue(columnType.isSupportScale());
        assertTrue(columnType.isSupportNullable());
        assertFalse(columnType.isSupportAutoIncrement());
    }

    @Test
    void nonNullableTypeWithoutPrecisionHasNoFlags() {
        Type type = Type.builder()
                .typeName("TIMESTAMP")
                .dataType(java.sql.Types.TIMESTAMP)
                .nullable((short) DatabaseMetaData.typeNoNulls)
                .build();

        ColumnType columnType = IGenericMetaDataConverter.INSTANCE.type2columnType(type);

        assertEquals("TIMESTAMP", columnType.getTypeName());
        assertFalse(columnType.isSupportLength());
        assertFalse(columnType.isSupportScale());
        assertFalse(columnType.isSupportNullable());
        assertFalse(columnType.isSupportAutoIncrement());
    }

    @Test
    void listMappingAppliesFlagsToEachElement() {
        Type withPrecision = Type.builder()
                .typeName("VARCHAR")
                .precision(255)
                .nullable((short) DatabaseMetaData.typeNullable)
                .build();
        Type plain = Type.builder()
                .typeName("BOOLEAN")
                .nullable((short) DatabaseMetaData.typeNullable)
                .build();

        List<ColumnType> columnTypes = IGenericMetaDataConverter.INSTANCE
                .type2columnType(List.of(withPrecision, plain));

        assertEquals(2, columnTypes.size());
        assertTrue(columnTypes.get(0).isSupportLength());
        assertTrue(columnTypes.get(0).isSupportNullable());
        assertFalse(columnTypes.get(1).isSupportLength());
        assertTrue(columnTypes.get(1).isSupportNullable());
    }
}
