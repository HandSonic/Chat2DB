package ai.chat2db.plugin.kylin;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for plugin:kylin-1: KylinMetaData must override tableDDL so that
 * Show Create Table / export / AI DDL tool do not crash with UnsupportedOperationException.
 */
class KylinMetaDataTest {

    @Test
    void tableDdlDoesNotThrowAndBuildsDdlFromJdbcMetadata() {
        Connection connection = connectionWithColumns(
                List.of(
                        Map.of("COLUMN_NAME", "ID", "TYPE_NAME", "BIGINT", "COLUMN_SIZE", 19, "NULLABLE", 0,
                                "REMARKS", "primary key"),
                        Map.of("COLUMN_NAME", "USER_NAME", "TYPE_NAME", "VARCHAR", "COLUMN_SIZE", 64, "NULLABLE", 1,
                                "REMARKS", "")));

        String ddl = assertDoesNotThrow(
                () -> new KylinMetaData().tableDDL(connection, "DEFAULT", "DEFAULT", "TEST_TABLE"));

        assertTrue(ddl.contains("CREATE TABLE TEST_TABLE"), "ddl should create the table: " + ddl);
        assertTrue(ddl.contains("ID BIGINT"), "ddl should contain the ID column: " + ddl);
        assertTrue(ddl.contains("NOT NULL"), "non-nullable column should be marked: " + ddl);
        assertTrue(ddl.contains("USER_NAME VARCHAR(64)"), "varchar size should be rendered: " + ddl);
    }

    private static Connection connectionWithColumns(List<Map<String, Object>> columnRows) {
        ResultSet columnsRs = resultSet(List.of("COLUMN_NAME", "TYPE_NAME", "COLUMN_SIZE", "NULLABLE", "REMARKS"),
                columnRows.stream()
                        .map(row -> List.of(row.get("COLUMN_NAME"), row.get("TYPE_NAME"), row.get("COLUMN_SIZE"),
                                row.get("NULLABLE"), row.get("REMARKS")))
                        .toList());
        ResultSet indexRs = resultSet(List.of("INDEX_NAME", "COLUMN_NAME", "NON_UNIQUE"), List.of());
        DatabaseMetaData metaData = proxy(DatabaseMetaData.class, (p, method, args) -> switch (method.getName()) {
            case "getColumns" -> columnsRs;
            case "getIndexInfo" -> indexRs;
            default -> defaultValue(method.getReturnType());
        });
        return proxy(Connection.class, (p, method, args) -> {
            if ("getMetaData".equals(method.getName())) {
                return metaData;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static ResultSet resultSet(List<String> labels, List<List<Object>> rows) {
        AtomicInteger cursor = new AtomicInteger(-1);
        ResultSetMetaData rsMetaData = proxy(ResultSetMetaData.class, (p, method, args) -> switch (method.getName()) {
            case "getColumnCount" -> labels.size();
            case "getColumnLabel", "getColumnName" -> labels.get((Integer) args[0] - 1);
            default -> defaultValue(method.getReturnType());
        });
        return proxy(ResultSet.class, (p, method, args) -> switch (method.getName()) {
            case "getMetaData" -> rsMetaData;
            case "next" -> cursor.incrementAndGet() < rows.size();
            case "getObject" -> rows.get(cursor.get()).get((Integer) args[0] - 1);
            case "close" -> null;
            default -> defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }
}
