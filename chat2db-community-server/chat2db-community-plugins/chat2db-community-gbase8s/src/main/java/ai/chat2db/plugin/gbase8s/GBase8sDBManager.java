package ai.chat2db.plugin.gbase8s;

import ai.chat2db.plugin.generic.GenericDBManager;
import ai.chat2db.spi.IDbManager;
import ai.chat2db.spi.model.datasource.ConnectInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;

@Slf4j
public class GBase8sDBManager extends GenericDBManager implements IDbManager {

    private static final String URL_PREFIX = "jdbc:gbasedbt-sqli://";
    private static final String SERVER_ATTRIBUTE = "GBASEDBTSERVER";

    @Override
    public Connection getConnection(ConnectInfo connectInfo) {
        connectInfo.setUrl(appendServerAttributeIfAbsent(connectInfo.getUrl(), connectInfo.getServiceName()));
        return super.getConnection(connectInfo);
    }

    static String appendServerAttributeIfAbsent(String url, String service) {
        if (StringUtils.isBlank(url) || StringUtils.isBlank(service)
                || !StringUtils.startsWithIgnoreCase(url, URL_PREFIX)) {
            return url;
        }

        int databaseSeparator = url.indexOf('/', URL_PREFIX.length());
        int attributesSeparator = databaseSeparator < 0 ? -1 : url.indexOf(':', databaseSeparator + 1);
        int querySeparator = url.indexOf('?');
        if (querySeparator >= 0 && (attributesSeparator < 0 || querySeparator < attributesSeparator)) {
            return url;
        }
        if (containsServerAttribute(url, attributesSeparator)) {
            return url;
        }

        // Informix-style URLs start the property list with ':' and separate later properties with ';'.
        String separator;
        if (attributesSeparator == url.length() - 1 || url.endsWith(";")) {
            separator = "";
        } else if (attributesSeparator >= 0) {
            separator = ";";
        } else {
            separator = ":";
        }
        return url + separator + SERVER_ATTRIBUTE + "=" + service;
    }

    private static boolean containsServerAttribute(String url, int attributesSeparator) {
        if (attributesSeparator < 0 || attributesSeparator == url.length() - 1) {
            return false;
        }
        String[] attributes = url.substring(attributesSeparator + 1).split(";", -1);
        for (String attribute : attributes) {
            int equals = attribute.indexOf('=');
            if (equals > 0 && SERVER_ATTRIBUTE.equalsIgnoreCase(attribute.substring(0, equals))) {
                return true;
            }
        }
        return false;
    }
}
