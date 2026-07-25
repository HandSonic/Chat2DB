package ai.chat2db.community.test.spi.sql;

import ai.chat2db.community.domain.api.config.DriverConfig;
import ai.chat2db.spi.DefaultSQLExecutor;
import ai.chat2db.spi.model.datasource.ConnectInfo;
import ai.chat2db.spi.model.request.FetchAllTableRecordsRequest;
import ai.chat2db.spi.sql.Chat2DBContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSQLExecutorTransactionTest {

    @Test
    void shouldNotCommitCallerManagedTransaction() throws Exception {
        String url = "jdbc:h2:mem:fetch_records_caller_commit;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url);
             Connection observer = DriverManager.getConnection(url)) {
            createTable(connection);
            connection.setAutoCommit(false);
            executeUpdate(connection, "INSERT INTO records VALUES (1)");
            AtomicReference<ResultSet> resultSet = new AtomicReference<>();

            DefaultSQLExecutor.getInstance().fetchAllTableRecords(request(connection, rs -> {
                resultSet.set(rs);
                while (rs.next()) {
                }
            }));

            assertFalse(connection.getAutoCommit());
            assertTrue(resultSet.get().isClosed());
            assertEquals(0, countRows(observer));
            connection.rollback();
        }
    }

    @Test
    void shouldNotRollbackCallerManagedTransaction() throws Exception {
        String url = "jdbc:h2:mem:fetch_records_caller_rollback;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url);
             Connection observer = DriverManager.getConnection(url)) {
            createTable(connection);
            connection.setAutoCommit(false);
            executeUpdate(connection, "INSERT INTO records VALUES (1)");

            assertThrows(RuntimeException.class, () -> DefaultSQLExecutor.getInstance()
                    .fetchAllTableRecords(request(connection, rs -> {
                        throw new IllegalStateException("consumer failed");
                    })));

            assertFalse(connection.getAutoCommit());
            connection.commit();
            assertEquals(1, countRows(observer));
        }
    }

    @Test
    void shouldRollbackAndRestoreTransactionStartedByExecutor() throws Exception {
        String url = "jdbc:h2:mem:fetch_records_executor_rollback;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url);
             Connection observer = DriverManager.getConnection(url)) {
            createTable(connection);

            assertThrows(RuntimeException.class, () -> DefaultSQLExecutor.getInstance()
                    .fetchAllTableRecords(request(connection, rs -> {
                        executeUpdate(connection, "INSERT INTO records VALUES (1)");
                        throw new IllegalStateException("consumer failed");
                    })));

            assertTrue(connection.getAutoCommit());
            assertEquals(0, countRows(observer));
        }
    }

    @Test
    void shouldDiscardConnectionAndPreserveRollbackFailure() throws Exception {
        String url = "jdbc:h2:mem:fetch_records_rollback_failure;DB_CLOSE_DELAY=-1";
        try (Connection delegate = DriverManager.getConnection(url);
             Connection observer = DriverManager.getConnection(url)) {
            createTable(delegate);
            Connection connection = proxyConnection(delegate, true, false);
            ConnectInfo connectInfo = putContext(connection);
            IllegalStateException consumerFailure = new IllegalStateException("consumer failed");
            try {
                RuntimeException thrown = assertThrows(RuntimeException.class,
                        () -> DefaultSQLExecutor.getInstance().fetchAllTableRecords(request(connection, rs -> {
                            executeUpdate(connection, "INSERT INTO records VALUES (1)");
                            throw consumerFailure;
                        })));

                assertSame(consumerFailure, thrown.getCause());
                assertEquals(1, consumerFailure.getSuppressed().length);
                assertEquals("rollback failed", consumerFailure.getSuppressed()[0].getMessage());
                assertTrue(connection.isClosed());
                assertNull(connectInfo.getConnection());
                assertEquals(0, countRows(observer));
            } finally {
                Chat2DBContext.removeContext();
            }
        }
    }

    @Test
    void shouldDiscardConnectionWhenAutoCommitRestorationFails() throws Exception {
        String url = "jdbc:h2:mem:fetch_records_restore_failure;DB_CLOSE_DELAY=-1";
        try (Connection delegate = DriverManager.getConnection(url)) {
            createTable(delegate);
            Connection connection = proxyConnection(delegate, false, true);
            ConnectInfo connectInfo = putContext(connection);
            try {
                RuntimeException thrown = assertThrows(RuntimeException.class,
                        () -> DefaultSQLExecutor.getInstance().fetchAllTableRecords(request(connection, rs -> {
                            while (rs.next()) {
                            }
                        })));

                assertTrue(thrown.getCause() instanceof SQLException);
                assertEquals("restore autoCommit failed", thrown.getCause().getMessage());
                assertTrue(connection.isClosed());
                assertNull(connectInfo.getConnection());
            } finally {
                Chat2DBContext.removeContext();
            }
        }
    }

    private static FetchAllTableRecordsRequest request(
            Connection connection, ai.chat2db.spi.IResultSetConsumer consumer) {
        return FetchAllTableRecordsRequest.builder()
                .connection(connection)
                .sql("SELECT * FROM records")
                .batchSize(10)
                .consumer(consumer)
                .build();
    }

    private static void createTable(Connection connection) throws java.sql.SQLException {
        executeUpdate(connection, "CREATE TABLE records (id INT PRIMARY KEY)");
    }

    private static void executeUpdate(Connection connection, String sql) throws java.sql.SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static int countRows(Connection connection) throws java.sql.SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM records")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static ConnectInfo putContext(Connection connection) {
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setConnection(connection);
        connectInfo.setDriverConfig(new DriverConfig());
        Chat2DBContext.putContext(connectInfo);
        return connectInfo;
    }

    private static Connection proxyConnection(
            Connection delegate, boolean failRollback, boolean failAutoCommitRestore) {
        AtomicBoolean autoCommitDisabled = new AtomicBoolean();
        return (Connection) Proxy.newProxyInstance(
                DefaultSQLExecutorTransactionTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if (failRollback && "rollback".equals(method.getName())) {
                        throw new SQLException("rollback failed");
                    }
                    if (failAutoCommitRestore && "setAutoCommit".equals(method.getName())) {
                        boolean autoCommit = (Boolean) args[0];
                        if (autoCommit && autoCommitDisabled.get()) {
                            throw new SQLException("restore autoCommit failed");
                        }
                        Object result = invoke(delegate, method, args);
                        if (!autoCommit) {
                            autoCommitDisabled.set(true);
                        }
                        return result;
                    }
                    return invoke(delegate, method, args);
                });
    }

    private static Object invoke(Connection delegate, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }
}
