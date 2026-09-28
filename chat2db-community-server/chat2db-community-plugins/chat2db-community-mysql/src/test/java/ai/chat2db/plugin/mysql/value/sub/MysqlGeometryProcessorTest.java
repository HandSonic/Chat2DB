package ai.chat2db.plugin.mysql.value.sub;

import ai.chat2db.spi.model.value.JDBCDataValue;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.WKBWriter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MysqlGeometryProcessorTest {

    private final MysqlGeometryProcessor processor = new MysqlGeometryProcessor();

    @Test
    void nullBinaryStreamYieldsSqlNullInsteadOfWrappedNullLiteral() {
        JDBCDataValue dataValue = new JDBCDataValue(null, null, 1, false) {
            @Override
            public InputStream getBinaryStream() {
                return null;
            }
        };
        assertEquals("NULL", processor.convertJDBCValueStrByType(dataValue));
    }

    @Test
    void validGeometryIsWrappedInGeomFromText() {
        byte[] wkb = new WKBWriter().write(new GeometryFactory().createPoint(new Coordinate(1, 2)));
        byte[] blob = new byte[4 + wkb.length];
        System.arraycopy(wkb, 0, blob, 4, wkb.length);
        JDBCDataValue dataValue = new JDBCDataValue(null, null, 1, false) {
            @Override
            public InputStream getBinaryStream() {
                return new ByteArrayInputStream(blob);
            }
        };
        assertEquals("ST_GeomFromText('POINT (1 2)')", processor.convertJDBCValueStrByType(dataValue));
    }
}
