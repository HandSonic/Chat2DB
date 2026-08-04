package ai.chat2db.plugin.gbase8s;

import ai.chat2db.spi.model.datasource.ConnectInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class GBase8sDBManagerTest {

    private static final String BASE_URL = "jdbc:gbasedbt-sqli://localhost:91088/mydb";

    private final GBase8sDBManager dbManager = new GBase8sDBManager();

    private static Connection openConnectionStub() {
        return (Connection) Proxy.newProxyInstance(GBase8sDBManagerTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("isClosed".equals(method.getName())) {
                        return false;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private ConnectInfo newConnectInfo(String url, String serviceName) {
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setUrl(url);
        connectInfo.setServiceName(serviceName);
        return connectInfo;
    }

    @Test
    void getConnectionAppliesServerAttributeOnceWithoutOpeningADriverConnection() {
        ConnectInfo connectInfo = newConnectInfo(BASE_URL, "svc");
        Connection connection = openConnectionStub();
        connectInfo.setConnection(connection);

        assertSame(connection, dbManager.getConnection(connectInfo));
        assertEquals(BASE_URL + ":GBASEDBTSERVER=svc", connectInfo.getUrl());

        assertSame(connection, dbManager.getConnection(connectInfo));
        assertEquals(BASE_URL + ":GBASEDBTSERVER=svc", connectInfo.getUrl());
    }

    @Test
    void existingPropertiesUseSemicolonForServerAttribute() {
        String url = BASE_URL + ":CLIENT_LOCALE=en_us.utf8;DB_LOCALE=en_us.utf8";

        assertEquals(url + ";GBASEDBTSERVER=svc",
                GBase8sDBManager.appendServerAttributeIfAbsent(url, "svc"));
        assertEquals(url + ";GBASEDBTSERVER=svc",
                GBase8sDBManager.appendServerAttributeIfAbsent(url + ";", "svc"));
    }

    @Test
    void existingServerAttributeIsRecognizedCaseInsensitively() {
        String lowerCase = BASE_URL + ":CLIENT_LOCALE=en_us.utf8;gbasedbtserver=custom";
        String mixedCase = BASE_URL + ":GBaseDbtServer=custom";

        assertEquals(lowerCase, GBase8sDBManager.appendServerAttributeIfAbsent(lowerCase, "svc"));
        assertEquals(mixedCase, GBase8sDBManager.appendServerAttributeIfAbsent(mixedCase, "svc"));
    }

    @Test
    void similarPropertyNamesAndValuesDoNotSuppressServerAttribute() {
        String similarName = BASE_URL + ":NOTGBASEDBTSERVER=custom";
        String similarValue = BASE_URL + ":CLIENT_LABEL=prefix:GBASEDBTSERVER=";
        String questionMarkValue = BASE_URL + ":CLIENT_LABEL=what?ever";

        assertEquals(similarName + ";GBASEDBTSERVER=svc",
                GBase8sDBManager.appendServerAttributeIfAbsent(similarName, "svc"));
        assertEquals(similarValue + ";GBASEDBTSERVER=svc",
                GBase8sDBManager.appendServerAttributeIfAbsent(similarValue, "svc"));
        assertEquals(questionMarkValue + ";GBASEDBTSERVER=svc",
                GBase8sDBManager.appendServerAttributeIfAbsent(questionMarkValue, "svc"));
    }

    @Test
    void nullBlankAndUnsupportedUrlsAreNotRewritten() {
        assertNull(GBase8sDBManager.appendServerAttributeIfAbsent(null, "svc"));
        assertEquals("", GBase8sDBManager.appendServerAttributeIfAbsent("", "svc"));
        assertEquals("  ", GBase8sDBManager.appendServerAttributeIfAbsent("  ", "svc"));
        assertEquals("not-a-jdbc-url",
                GBase8sDBManager.appendServerAttributeIfAbsent("not-a-jdbc-url", "svc"));

        String queryStyleUrl = BASE_URL + "?CLIENT_LOCALE=en_us.utf8";
        assertEquals(queryStyleUrl, GBase8sDBManager.appendServerAttributeIfAbsent(queryStyleUrl, "svc"));
    }

    @Test
    void blankServiceNameLeavesUrlUntouched() {
        assertEquals(BASE_URL, GBase8sDBManager.appendServerAttributeIfAbsent(BASE_URL, null));
        assertEquals(BASE_URL, GBase8sDBManager.appendServerAttributeIfAbsent(BASE_URL, " "));
    }
}
