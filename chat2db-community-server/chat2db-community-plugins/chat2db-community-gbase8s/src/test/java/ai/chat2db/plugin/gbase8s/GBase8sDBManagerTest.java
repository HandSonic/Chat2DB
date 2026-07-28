package ai.chat2db.plugin.gbase8s;

import ai.chat2db.community.domain.api.config.DriverConfig;
import ai.chat2db.spi.model.datasource.ConnectInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GBase8sDBManagerTest {

    private static final String BASE_URL = "jdbc:gbasedbt-sqli://localhost:91088/mydb";

    private final GBase8sDBManager dbManager = new GBase8sDBManager();

    private ConnectInfo newConnectInfo(String url, String serviceName) {
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setUrl(url);
        connectInfo.setServiceName(serviceName);
        connectInfo.setDbType("GBASE8S");
        // Unloadable driver so getConnection fails fast without any network access,
        // after the subclass has applied its URL rewrite.
        DriverConfig driverConfig = new DriverConfig();
        driverConfig.setJdbcDriver("gbase8s-test-nonexistent-driver.jar");
        driverConfig.setJdbcDriverClass("com.nonexistent.Driver");
        connectInfo.setDriverConfig(driverConfig);
        return connectInfo;
    }

    @Test
    void serverAttributeIsAppendedOnlyOnceAcrossReconnects() {
        ConnectInfo connectInfo = newConnectInfo(BASE_URL, "svc");

        assertThrows(Exception.class, () -> dbManager.getConnection(connectInfo));
        assertEquals(BASE_URL + ":GBASEDBTSERVER=svc", connectInfo.getUrl());

        // ConnectionPool re-enters getConnection with the same ConnectInfo after the cached
        // connection drops; the URL must not grow a duplicate GBASEDBTSERVER segment.
        assertThrows(Exception.class, () -> dbManager.getConnection(connectInfo));
        assertEquals(BASE_URL + ":GBASEDBTSERVER=svc", connectInfo.getUrl());
    }

    @Test
    void urlAlreadyContainingServerAttributeIsLeftAlone() {
        ConnectInfo connectInfo = newConnectInfo(BASE_URL + ":GBASEDBTSERVER=svc", "svc");

        assertThrows(Exception.class, () -> dbManager.getConnection(connectInfo));
        assertEquals(BASE_URL + ":GBASEDBTSERVER=svc", connectInfo.getUrl());
    }

    @Test
    void blankServiceNameLeavesUrlUntouched() {
        ConnectInfo connectInfo = newConnectInfo(BASE_URL, " ");

        assertThrows(Exception.class, () -> dbManager.getConnection(connectInfo));
        assertEquals(BASE_URL, connectInfo.getUrl());
    }
}
