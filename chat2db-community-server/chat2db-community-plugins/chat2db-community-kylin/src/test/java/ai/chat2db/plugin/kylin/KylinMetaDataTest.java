package ai.chat2db.plugin.kylin;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test for plugin:kylin-1: KylinMetaData must override tableDDL so that
 * Show Create Table / export / AI DDL tool do not crash with UnsupportedOperationException.
 */
class KylinMetaDataTest {

    @Test
    void tableDdlQuotesIdentifiersAndEscapesCommentLiterals() {
        Connection connection = connectionWithMetadata(
                List.of(
                        List.<Object>of("display name", "VARCHAR", 64, 0, 1, "employee's id"),
                        List.<Object>of("a\"b", "DECIMAL", 10, 2, 0, "")),
                List.of(
                        List.<Object>of("select", "display name", false, (short) 1),
                        List.<Object>of("select", "a\"b", false, (short) 2)));

        String ddl = new KylinMetaData().tableDDL(connection, "DEFAULT", "DEFAULT", "order");

        assertEquals("CREATE TABLE \"order\" (\n"
                + "\t\"display name\" VARCHAR(64) COMMENT 'employee''s id',\n"
                + "\t\"a\"\"b\" DECIMAL(10,2) NOT NULL\n"
                + ");\n"
                + "CREATE UNIQUE INDEX \"select\" ON \"order\" (\"display name\", \"a\"\"b\");", ddl);
    }

    private static Connection connectionWithMetadata(List<List<Object>> columnRows, List<List<Object>> indexRows) {
        ResultSet columnsRs = resultSet(
                List.of("COLUMN_NAME", "TYPE_NAME", "COLUMN_SIZE", "DECIMAL_DIGITS", "NULLABLE", "REMARKS"),
                columnRows);
        ResultSet indexRs = resultSet(
                List.of("INDEX_NAME", "COLUMN_NAME", "NON_UNIQUE", "ORDINAL_POSITION"), indexRows);
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
