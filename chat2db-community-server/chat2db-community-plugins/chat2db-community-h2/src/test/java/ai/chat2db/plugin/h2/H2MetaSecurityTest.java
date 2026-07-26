package ai.chat2db.plugin.h2;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class H2MetaSecurityTest {

    @Test
    void tableDDLEscapesColumnNameAndRemarksAttacks() {
        Map<String, Object> column = new HashMap<>();
        column.put("COLUMN_NAME", "C\"; DROP TABLE U; --");
        column.put("TYPE_NAME", "INTEGER");
        column.put("COLUMN_SIZE", 32);
        column.put("NULLABLE", ResultSetMetaData.columnNoNulls);
        column.put("REMARKS", "x'); DROP TABLE U; --");

        String ddl = new H2Meta().tableDDL(connection(List.of(column), List.of()), "TEST", "PUBLIC", "USERS");

        assertEquals("CREATE TABLE \"USERS\" (\n"
            + "\"C\"\"; DROP TABLE U; --\" INTEGER(32) NOT NULL COMMENT 'x''); DROP TABLE U; --'\n"
            + ");\n", ddl);
    }

    @Test
    void tableDDLNeutralizesHostileColumnDefaults() {
        Map<String, Object> wrappedAttack = baseColumn();
        wrappedAttack.put("COLUMN_DEF", "'x'); DROP TABLE U; --'");
        String ddl = new H2Meta().tableDDL(connection(List.of(wrappedAttack), List.of()), "TEST", "PUBLIC", "USERS");
        assertEquals("CREATE TABLE \"USERS\" (\n"
            + "\"ID\" INTEGER(32) NOT NULL DEFAULT 'x''); DROP TABLE U; --'\n"
            + ");\n", ddl);

        Map<String, Object> bareAttack = baseColumn();
        bareAttack.put("COLUMN_DEF", "0; DROP TABLE U; --");
        ddl = new H2Meta().tableDDL(connection(List.of(bareAttack), List.of()), "TEST", "PUBLIC", "USERS");
        assertEquals("CREATE TABLE \"USERS\" (\n"
            + "\"ID\" INTEGER(32) NOT NULL DEFAULT '0; DROP TABLE U; --'\n"
            + ");\n", ddl);
    }

    @Test
    void tableDDLPreservesBenignColumnDefaults() {
        Map<String, Object> literal = baseColumn();
        literal.put("COLUMN_DEF", "'O''Brien'");
        String ddl = new H2Meta().tableDDL(connection(List.of(literal), List.of()), "TEST", "PUBLIC", "USERS");
        assertEquals("CREATE TABLE \"USERS\" (\n"
            + "\"ID\" INTEGER(32) NOT NULL DEFAULT 'O''Brien'\n"
            + ");\n", ddl);

        Map<String, Object> expression = baseColumn();
        expression.put("COLUMN_DEF", "CURRENT_TIMESTAMP");
        ddl = new H2Meta().tableDDL(connection(List.of(expression), List.of()), "TEST", "PUBLIC", "USERS");
        assertEquals("CREATE TABLE \"USERS\" (\n"
            + "\"ID\" INTEGER(32) NOT NULL DEFAULT CURRENT_TIMESTAMP\n"
            + ");\n", ddl);
    }

    @Test
    void tableDDLFailsClosedOnHostileTypeName() {
        Map<String, Object> column = baseColumn();
        column.put("TYPE_NAME", "INT; DROP TABLE U; --");

        String ddl = new H2Meta().tableDDL(connection(List.of(column), List.of()), "TEST", "PUBLIC", "USERS");

        assertEquals("", ddl);
    }

    @Test
    void tableDDLEscapesIndexNamesAndColumns() {
        Map<String, Object> index = new HashMap<>();
        index.put("INDEX_NAME", "I\"; DROP TABLE U; --");
        index.put("COLUMN_NAME", "C\"; X");

        String ddl = new H2Meta().tableDDL(connection(List.of(baseColumn()), List.of(index)), "TEST", "PUBLIC",
            "USERS");

        assertEquals("CREATE TABLE \"USERS\" (\n"
            + "\"ID\" INTEGER(32) NOT NULL\n"
            + ");\n"
            + "CREATE INDEX \"I\"\"; DROP TABLE U; --\" ON \"USERS\" (\"C\"\"; X\");", ddl);
    }

    private static Map<String, Object> baseColumn() {
        Map<String, Object> column = new HashMap<>();
        column.put("COLUMN_NAME", "ID");
        column.put("TYPE_NAME", "INTEGER");
        column.put("COLUMN_SIZE", 32);
        column.put("NULLABLE", ResultSetMetaData.columnNoNulls);
        return column;
    }

    private static Connection connection(List<Map<String, Object>> columns, List<Map<String, Object>> indexes) {
        ResultSet columnsRs = resultSet(columns);
        ResultSet indexesRs = resultSet(indexes);
        DatabaseMetaData metaData = proxy(DatabaseMetaData.class, (p, method, args) -> {
            if ("getColumns".equals(method.getName())) {
                return columnsRs;
            }
            if ("getIndexInfo".equals(method.getName())) {
                return indexesRs;
            }
            return defaultValue(method.getReturnType());
        });
        return proxy(Connection.class, (p, method, args) -> {
            if ("getMetaData".equals(method.getName())) {
                return metaData;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static ResultSet resultSet(List<Map<String, Object>> rows) {
        AtomicInteger row = new AtomicInteger(-1);
        return proxy(ResultSet.class, (p, method, args) -> {
            switch (method.getName()) {
                case "next":
                    return row.incrementAndGet() < rows.size();
                case "getString":
                    Object stringValue = rows.get(row.get()).get((String) args[0]);
                    return stringValue == null ? null : stringValue.toString();
                case "getInt":
                    Object intValue = rows.get(row.get()).get((String) args[0]);
                    return intValue == null ? 0 : ((Number) intValue).intValue();
                default:
                    return defaultValue(method.getReturnType());
            }
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
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
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        return 0D;
    }
}
