package etcd.server.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class URLUtil {

    private static final Logger logger = LoggerFactory.getLogger(URLUtil.class);

    public static RemotingUrl parseHostAndPort(String url){
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
            String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
            int port = uri.getPort();
            if (port == -1) {
                if ("http".equalsIgnoreCase(scheme)) {
                    port = 80;
                } else if ("https".equalsIgnoreCase(scheme)) {
                    port = 443;
                }
            }
            RemotingUrl remotingUrl = new RemotingUrl();
            remotingUrl.setDomain(url);
            remotingUrl.setHost(host);
            remotingUrl.setPort(port);
            return remotingUrl;
        }catch (Exception e){
            logger.error("parse host and port error! {}",e);
            return null;
        }
    }

    public static void main(String[] args) {
        System.out.println(URLUtil.parseHostAndPort("http://10.11.161.70:2379"));
    }

}
