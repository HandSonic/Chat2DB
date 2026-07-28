package ai.chat2db.plugin.kylin;

import ai.chat2db.community.domain.api.config.DriverConfig;
import ai.chat2db.community.domain.api.model.async.AsyncContext;
import ai.chat2db.spi.model.datasource.ConnectInfo;
import ai.chat2db.spi.sql.Chat2DBContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for plugin:kylin-2: Kylin export must not emit the MySQL-only
 * SET FOREIGN_KEY_CHECKS statements into dump files.
 */
class KylinDBManagerTest {

    private static final String DB_TYPE = "KYLIN";

    @BeforeEach
    void setUpContext() {
        Chat2DBContext.PLUGIN_MAP.put(DB_TYPE, new KylinPlugin());
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setDbType(DB_TYPE);
        connectInfo.setDriverConfig(new DriverConfig());
        Chat2DBContext.putContext(connectInfo);
    }

    @AfterEach
    void tearDownContext() {
        Chat2DBContext.removeContext();
        Chat2DBContext.PLUGIN_MAP.remove(DB_TYPE);
    }

    @Test
    void exportDatabaseDoesNotWriteMysqlForeignKeyChecks() throws Exception {
        File dumpFile = File.createTempFile("kylin-export", ".sql");
        dumpFile.deleteOnExit();
        try {
            AsyncContext asyncContext = new AsyncContext(null, null, dumpFile, false);
            new KylinDBManager().exportDatabase(connectionWithNoTables(), "DEFAULT", "DEFAULT", asyncContext);
            asyncContext.finish();

            String dump = Files.readString(dumpFile.toPath(), StandardCharsets.UTF_8);
            assertFalse(dump.contains("FOREIGN_KEY_CHECKS"),
                    "Kylin dump must not contain MySQL-only SET FOREIGN_KEY_CHECKS statements: " + dump);
            assertTrue(dump.contains("Chat2DB export data"), "export header should still be written: " + dump);
        } finally {
            Files.deleteIfExists(dumpFile.toPath());
        }
    }

    private static Connection connectionWithNoTables() {
        ResultSet emptyTables = emptyResultSet(List.of("TABLE_NAME"));
        DatabaseMetaData metaData = proxy(DatabaseMetaData.class, (p, method, args) -> {
            if ("getTables".equals(method.getName())) {
                return emptyTables;
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

    private static ResultSet emptyResultSet(List<String> labels) {
        AtomicInteger cursor = new AtomicInteger(-1);
        ResultSetMetaData rsMetaData = proxy(ResultSetMetaData.class, (p, method, args) -> switch (method.getName()) {
            case "getColumnCount" -> labels.size();
            case "getColumnLabel", "getColumnName" -> labels.get((Integer) args[0] - 1);
            default -> defaultValue(method.getReturnType());
        });
        return proxy(ResultSet.class, (p, method, args) -> switch (method.getName()) {
            case "getMetaData" -> rsMetaData;
            case "next" -> false;
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
