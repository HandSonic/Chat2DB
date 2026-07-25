package ai.chat2db.spi.sql;

import ai.chat2db.spi.model.datasource.ConnectInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionPoolTest {

    @Test
    void cleanupShouldCloseAndRemoveInvalidIdleConnection() {
        AtomicBoolean closed = new AtomicBoolean();
        ConnectInfo connectInfo = connectInfo(connection(false, closed));
        LinkedBlockingQueue<ConnectInfo> queue = new LinkedBlockingQueue<>();
        queue.offer(connectInfo);

        ConnectionPool.cleanupQueue(queue);

        assertTrue(queue.isEmpty());
        assertTrue(closed.get());
        assertNull(connectInfo.getConnection());
    }

    @Test
    void cleanupShouldCloseExpiredConnectionEvenWhenItIsValid() {
        AtomicBoolean closed = new AtomicBoolean();
        ConnectInfo connectInfo = connectInfo(connection(true, closed));
        connectInfo.setLastAccessTime(new Date(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(31)));
        LinkedBlockingQueue<ConnectInfo> queue = ConnectionPool.newConnectionQueue();
        queue.offer(connectInfo);

        ConnectionPool.cleanupQueue(queue);

        assertTrue(queue.isEmpty());
        assertTrue(closed.get());
        assertNull(connectInfo.getConnection());
    }

    @Test
    void cleanupShouldLeaveBorrowedConnectionInQueue() {
        ConnectInfo connectInfo = connectInfo(connection(true, new AtomicBoolean()));
        assertTrue(connectInfo.trySetInUse());
        LinkedBlockingQueue<ConnectInfo> queue = new LinkedBlockingQueue<>();
        queue.offer(connectInfo);

        ConnectionPool.cleanupQueue(queue);

        assertSame(connectInfo, queue.peek());
        connectInfo.releaseInUse();
    }

    @Test
    void failedBorrowShouldReturnConnectionInfoToQueue() {
        ConnectInfo pooled = connectInfo(connection(true, new AtomicBoolean()));
        assertTrue(pooled.trySetInUse());
        LinkedBlockingQueue<ConnectInfo> queue = new LinkedBlockingQueue<>();
        queue.offer(pooled);

        assertNull(ConnectionPool.tryBorrowConnection(new ConnectInfo(), queue));
        assertSame(pooled, queue.peek());
        pooled.releaseInUse();
    }

    @Test
    void staleConnectionShouldBeValidatedBeforeBorrow() {
        AtomicBoolean closed = new AtomicBoolean();
        AtomicInteger validationCalls = new AtomicInteger();
        ConnectInfo pooled = connectInfo(connection(false, closed, validationCalls));
        pooled.setLastAccessTime(new Date(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(1)));
        LinkedBlockingQueue<ConnectInfo> queue = ConnectionPool.newConnectionQueue();
        queue.offer(pooled);

        assertNull(ConnectionPool.tryBorrowConnection(new ConnectInfo(), queue));

        assertEquals(1, validationCalls.get());
        assertTrue(closed.get());
        assertNull(pooled.getConnection());
    }

    @Test
    void concurrentReturnsShouldKeepQueueBoundedAndCloseOverflowConnections() throws Exception {
        int connectionCount = 32;
        LinkedBlockingQueue<ConnectInfo> queue = ConnectionPool.newConnectionQueue();
        List<ConnectInfo> connectInfos = new ArrayList<>(connectionCount);
        List<AtomicBoolean> closedConnections = new ArrayList<>(connectionCount);
        for (int i = 0; i < connectionCount; i++) {
            AtomicBoolean closed = new AtomicBoolean();
            closedConnections.add(closed);
            connectInfos.add(connectInfo(connection(true, closed)));
        }

        ExecutorService executor = Executors.newFixedThreadPool(connectionCount);
        CountDownLatch ready = new CountDownLatch(connectionCount);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>(connectionCount);
            for (ConnectInfo connectInfo : connectInfos) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    ConnectionPool.offerOrClose(queue, connectInfo);
                    return null;
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }

            assertEquals(2, queue.size());
            Set<ConnectInfo> retained = Collections.newSetFromMap(new IdentityHashMap<>());
            retained.addAll(queue);
            for (int i = 0; i < connectionCount; i++) {
                assertEquals(!retained.contains(connectInfos.get(i)), closedConnections.get(i).get());
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private static ConnectInfo connectInfo(Connection connection) {
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setConnection(connection);
        connectInfo.setLastAccessTime(new Date());
        return connectInfo;
    }

    private static Connection connection(boolean valid, AtomicBoolean closed) {
        return connection(valid, closed, new AtomicInteger());
    }

    private static Connection connection(boolean valid, AtomicBoolean closed, AtomicInteger validationCalls) {
        return (Connection) Proxy.newProxyInstance(
                ConnectionPoolTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isClosed":
                            return closed.get();
                        case "isValid":
                            validationCalls.incrementAndGet();
                            return valid;
                        case "close":
                            closed.set(true);
                            return null;
                        case "toString":
                            return "TestConnection";
                        default:
                            return defaultValue(method.getReturnType());
                    }
                });
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
