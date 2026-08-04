package ai.chat2db.plugin.gbase8s;

import ai.chat2db.plugin.generic.GenericDBManager;
import ai.chat2db.spi.IDbManager;
import ai.chat2db.spi.model.datasource.ConnectInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;

@Slf4j
public class GBase8sDBManager extends GenericDBManager implements IDbManager {

    @Override
    public Connection getConnection(ConnectInfo connectInfo) {
        appendServerAttributeIfAbsent(connectInfo);
        return super.getConnection(connectInfo);
    }

    static void appendServerAttributeIfAbsent(ConnectInfo connectInfo) {
        String url = connectInfo.getUrl();
        String service = connectInfo.getServiceName();
        // The shared ConnectInfo is reused on every (re)connect, so append the server
        // attribute at most once; otherwise each reconnect adds another ':GBASEDBTSERVER=' segment.
        if (StringUtils.isNotBlank(service) && url != null && !url.contains("GBASEDBTSERVER=")) {
            connectInfo.setUrl(url + ":" + "GBASEDBTSERVER=" + service);
        }
    }
}
