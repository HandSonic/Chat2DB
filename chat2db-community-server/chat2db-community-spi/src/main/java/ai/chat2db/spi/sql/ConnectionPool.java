package ai.chat2db.spi.sql;

import ai.chat2db.community.domain.api.enums.parser.DatabaseTypeEnum;
import ai.chat2db.spi.model.datasource.ConnectInfo;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
public class ConnectionPool {

    private static final int MAX_CONNECTIONS = 2;


    private static final int VALIDATION_TIMEOUT_SECONDS = 2;


    private static final long SKIP_VALIDATION_IF_RECENTLY_USED_MS = 30 * 1000L;

    private static final String DEFAULT_VALIDATION_SQL = "select 1";

    private static final String ORACLE_VALIDATION_SQL = "SELECT 1 FROM DUAL";

    private static ConcurrentHashMap<Long, ConcurrentHashMap<String, LinkedBlockingQueue<ConnectInfo>>> CONNECTION_MAP = new ConcurrentHashMap<>();


    static {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000 * 60 * 1);
                    cleanupConnections();
                } catch (Exception e) {
                    log.error("close connection error", e);
                }
            }
        }).start();
    }

    static void cleanupConnections() {
        log.info("CONNECTION_MAP size:{}", CONNECTION_MAP.size());
        for (Map.Entry<Long, ConcurrentHashMap<String, LinkedBlockingQueue<ConnectInfo>>> entry : CONNECTION_MAP.entrySet()) {
            ConcurrentHashMap<String, LinkedBlockingQueue<ConnectInfo>> map = entry.getValue();
            if (map == null) {
                continue;
            }
            for (Map.Entry<String, LinkedBlockingQueue<ConnectInfo>> queueEntry : map.entrySet()) {
                LinkedBlockingQueue<ConnectInfo> queue = queueEntry.getValue();
                if (queue == null) {
                    continue;
                }
                log.info(queueEntry.getKey() + " queue size:{}", queue.size());
                cleanupQueue(entry.getKey(), queueEntry.getKey(), queue);
            }
        }
    }

    static void cleanupQueue(LinkedBlockingQueue<ConnectInfo> queue) {
        cleanupQueue(null, null, queue);
    }

    private static void cleanupQueue(Long datasourceId, String connectionKey,
                                     LinkedBlockingQueue<ConnectInfo> queue) {
        int connectionsToCheck = queue.size();
        for (int i = 0; i < connectionsToCheck; i++) {
            ConnectInfo connectInfo = queue.poll();
            if (connectInfo == null) {
                return;
            }
            log.info("check connection:{},usage:{}", connectInfo.getKey(), connectInfo.isInUse());
            Date lastAccessTime = connectInfo.getLastAccessTime();
            if (!connectInfo.trySetInUse()) {
                returnToQueueOrClose(datasourceId, connectionKey, queue, connectInfo);
                continue;
            }
            boolean reusable = true;
            try {
                boolean expired = lastAccessTime != null
                        && lastAccessTime.getTime() + 1000 * 60 * 30 < System.currentTimeMillis();
                if (expired || !checkConnectionIsActive(connectInfo)) {
                    closeQuietly(connectInfo);
                    reusable = false;
                }
            } finally {
                connectInfo.releaseInUse();
                if (reusable) {
                    returnToQueueOrClose(datasourceId, connectionKey, queue, connectInfo);
                }
            }
        }
    }

    static LinkedBlockingQueue<ConnectInfo> newConnectionQueue() {
        return new LinkedBlockingQueue<>(MAX_CONNECTIONS);
    }

    static void offerOrClose(LinkedBlockingQueue<ConnectInfo> queue, ConnectInfo connectInfo) {
        if (!queue.offer(connectInfo)) {
            closeQuietly(connectInfo);
        }
    }

    private static void returnToQueueOrClose(Long datasourceId, String connectionKey,
                                             LinkedBlockingQueue<ConnectInfo> queue,
                                             ConnectInfo connectInfo) {
        if (datasourceId == null) {
            offerOrClose(queue, connectInfo);
            return;
        }
        boolean[] returnedToCurrentQueue = {false};
        CONNECTION_MAP.computeIfPresent(datasourceId, (key, keyMap) -> {
            if (keyMap.get(connectionKey) == queue) {
                returnedToCurrentQueue[0] = queue.offer(connectInfo);
            }
            return keyMap;
        });
        if (!returnedToCurrentQueue[0]) {
            closeQuietly(connectInfo);
        }
    }

    private static boolean checkConnectionIsActive(ConnectInfo connectInfo) {
        try {
            Connection connection = connectInfo.getConnection();
            if (connection == null || connection.isClosed()) {
                return false;
            }
            try {
                return connection.isValid(VALIDATION_TIMEOUT_SECONDS);
            } catch (Throwable ignore) {
                return validateByProbeSql(connectInfo, connection);
            }
        } catch (Exception e) {
            log.error("check connection error,connectInfo:{}", connectInfo.getKey(), e);
            return false;
        }
    }

    private static boolean validateByProbeSql(ConnectInfo connectInfo, Connection connection) {
        String sql = DEFAULT_VALIDATION_SQL;
        try {
            if (DatabaseTypeEnum.HIVE.name().equals(connectInfo.getDbType())
                    || DatabaseTypeEnum.KYLIN.name().equals(connectInfo.getDbType())
                    || DatabaseTypeEnum.PRESTO.name().equals(connectInfo.getDbType())
                    || DatabaseTypeEnum.SUNDB.name().equals(connectInfo.getDbType())
            ) {
                connection.getMetaData().getCatalogs();
                return true;
            }
            if (DatabaseTypeEnum.ORACLE.name().equals(connectInfo.getDbType())
                || DatabaseTypeEnum.OSCAR.name().equals(connectInfo.getDbType())
                || DatabaseTypeEnum.GBASE8S.name().equals(connectInfo.getDbType())
            ) {
                sql = ORACLE_VALIDATION_SQL;
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(VALIDATION_TIMEOUT_SECONDS);
                statement.execute();
                return true;
            }
        } catch (Exception e) {
            log.error("check connection error,sql:{},connectInfo:{}", sql, connectInfo.getKey(), e);
            return false;
        }
    }


    private static boolean isRecentlyUsed(Date lastAccessTime) {
        return lastAccessTime != null
                && System.currentTimeMillis() - lastAccessTime.getTime() < SKIP_VALIDATION_IF_RECENTLY_USED_MS;
    }


    private static Connection tryGetExistingConnection(ConnectInfo connectInfo) {
        try {
            Connection conn = connectInfo.getConnection();
            if (conn != null && !conn.isClosed()) {
                return conn;
            }
        } catch (SQLException e) {
            log.warn("Error checking existing connection", e);
        }
        return null;
    }

    public static Connection createNewConnection(ConnectInfo connectInfo) {
        log.info("Creating new individual connection");
        Connection connection = Chat2DBContext.getDbManager(connectInfo.getDbType()).getConnection(connectInfo);
        connectInfo.setConnection(connection);
        return connection;
    }

    public static Connection getConnection(ConnectInfo connectInfo) {
        Connection connection = tryGetExistingConnection(connectInfo);
        if (connection != null) {
            return connection;
        }
        ConcurrentHashMap<String, LinkedBlockingQueue<ConnectInfo>> map = CONNECTION_MAP.get(connectInfo.getDataSourceId());
        if (map == null) {
            return createNewConnection(connectInfo);
        }
        LinkedBlockingQueue<ConnectInfo> queue = map.get(connectInfo.getKey());
        if (queue != null) {
            Connection pooledConnection = tryBorrowConnection(connectInfo, connectInfo.getDataSourceId(),
                    connectInfo.getKey(), queue);
            if (pooledConnection != null) {
                return pooledConnection;
            }
        }
        return createNewConnection(connectInfo);
    }

    static Connection tryBorrowConnection(ConnectInfo connectInfo, LinkedBlockingQueue<ConnectInfo> queue) {
        return tryBorrowConnection(connectInfo, null, null, queue);
    }

    private static Connection tryBorrowConnection(ConnectInfo connectInfo, Long datasourceId, String connectionKey,
                                                   LinkedBlockingQueue<ConnectInfo> queue) {
        ConnectInfo pooledConnectInfo = queue.poll();
        if (pooledConnectInfo == null) {
            return null;
        }
        Date lastAccessTime = pooledConnectInfo.getLastAccessTime();
        if (!pooledConnectInfo.trySetInUse()) {
            returnToQueueOrClose(datasourceId, connectionKey, queue, pooledConnectInfo);
            return null;
        }
        Connection connection = tryGetExistingConnection(pooledConnectInfo);
        if (connection != null
                && (isRecentlyUsed(lastAccessTime) || checkConnectionIsActive(pooledConnectInfo))) {
            connectInfo.setConnection(connection);
            log.info("Got connection from pool");
            return connection;
        }
        closeQuietly(pooledConnectInfo);
        return null;
    }

    public static void removeConnection(Long datasourceId) {
        CONNECTION_MAP.computeIfPresent(datasourceId, (key, keyMap) -> {
            for (Map.Entry<String, LinkedBlockingQueue<ConnectInfo>> entry : keyMap.entrySet()) {
                LinkedBlockingQueue<ConnectInfo> queue = entry.getValue();
                ConnectInfo connectInfo = queue.poll();
                while (connectInfo != null) {
                    closeQuietly(connectInfo);
                    connectInfo = queue.poll();
                }
            }
            return null;
        });
    }


    private static void closeQuietly(ConnectInfo connectInfo) {
        if (connectInfo == null) {
            return;
        }
        try {
            Connection connection = connectInfo.getConnection();
            if (connection != null && !connection.isClosed()) {
                connectInfo.setConnection(null);
                connection.close();
            }
        } catch (Exception e) {
            log.error("close connection error", e);
        }
    }


    public static void close(ConnectInfo connectInfo) {
        connectInfo.setLastAccessTime(new Date());
        connectInfo.releaseInUse();

        if (DatabaseTypeEnum.MONGODB.name().equals(connectInfo.getDbType())
                || DatabaseTypeEnum.REDIS.name().equals(connectInfo.getDbType())
                || connectInfo.getConnection() == null
        ) {
            closeQuietly(connectInfo);
            return;
        }
        try {
            if (connectInfo.getConnection().isClosed()) {
                closeQuietly(connectInfo);
                return;
            }
        } catch (Exception e) {
            closeQuietly(connectInfo);
            return;
        }

        String key = connectInfo.getKey();
        ConcurrentHashMap<String, LinkedBlockingQueue<ConnectInfo>> map = CONNECTION_MAP.computeIfAbsent(connectInfo.getDataSourceId(), k -> new ConcurrentHashMap<>());
        LinkedBlockingQueue<ConnectInfo> queue = map.computeIfAbsent(key, k -> newConnectionQueue());
        offerOrClose(queue, connectInfo);
    }

}
