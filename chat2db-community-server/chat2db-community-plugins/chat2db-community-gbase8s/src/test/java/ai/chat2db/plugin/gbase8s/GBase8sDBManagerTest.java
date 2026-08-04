package ai.chat2db.plugin.gbase8s;

import ai.chat2db.spi.model.datasource.ConnectInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GBase8sDBManagerTest {

    private static final String BASE_URL = "jdbc:gbasedbt-sqli://localhost:91088/mydb";

    private ConnectInfo newConnectInfo(String url, String serviceName) {
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setUrl(url);
        connectInfo.setServiceName(serviceName);
        return connectInfo;
    }

    @Test
    void serverAttributeIsAppendedOnlyOnceAcrossReconnects() {
        ConnectInfo connectInfo = newConnectInfo(BASE_URL, "svc");

        GBase8sDBManager.appendServerAttributeIfAbsent(connectInfo);
        assertEquals(BASE_URL + ":GBASEDBTSERVER=svc", connectInfo.getUrl());

        // ConnectionPool re-enters getConnection with the same ConnectInfo after the cached
        // connection drops; the URL must not grow a duplicate GBASEDBTSERVER segment.
        GBase8sDBManager.appendServerAttributeIfAbsent(connectInfo);
        assertEquals(BASE_URL + ":GBASEDBTSERVER=svc", connectInfo.getUrl());
    }

    @Test
    void urlAlreadyContainingServerAttributeIsLeftAlone() {
        ConnectInfo connectInfo = newConnectInfo(BASE_URL + ":GBASEDBTSERVER=svc", "svc");

        GBase8sDBManager.appendServerAttributeIfAbsent(connectInfo);
        assertEquals(BASE_URL + ":GBASEDBTSERVER=svc", connectInfo.getUrl());
    }

    @Test
    void blankServiceNameLeavesUrlUntouched() {
        ConnectInfo connectInfo = newConnectInfo(BASE_URL, " ");

        GBase8sDBManager.appendServerAttributeIfAbsent(connectInfo);
        assertEquals(BASE_URL, connectInfo.getUrl());
    }
}
