package ai.chat2db.plugin.sqlserver;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlServerMetaDataTest {

    @Test
    void shouldPreserveEitherNonDefaultIdentityParameterWithoutIntegerTruncation() {
        assertEquals("BIGINT identity",
                SqlServerMetaData.buildIdentityDataType("BIGINT", BigDecimal.ONE, BigDecimal.ONE));
        assertEquals("BIGINT identity (10,1)",
                SqlServerMetaData.buildIdentityDataType("BIGINT", BigDecimal.TEN, BigDecimal.ONE));
        assertEquals("BIGINT identity (1,5)",
                SqlServerMetaData.buildIdentityDataType("BIGINT", BigDecimal.ONE, BigDecimal.valueOf(5)));
        assertEquals("DECIMAL identity (9223372036854775808,-2)",
                SqlServerMetaData.buildIdentityDataType("DECIMAL",
                        new BigDecimal("9223372036854775808"), new BigDecimal("-2")));
    }

    @Test
    void shouldNotRenderJdbcLengthForFixedSizeOrRowVersionTypes() {
        assertTrue(SqlServerMetaData.shouldOmitColumnSize("TIMESTAMP"));
        assertTrue(SqlServerMetaData.shouldOmitColumnSize("ROWVERSION"));
        assertTrue(SqlServerMetaData.shouldOmitColumnSize("FLOAT"));
        assertFalse(SqlServerMetaData.shouldOmitColumnSize("DECIMAL"));
    }
}
