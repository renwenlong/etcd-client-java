package etcd.server.util;

import com.google.common.collect.Lists;
import io.netty.handler.codec.http.QueryStringEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class EtcdUtil {
    private static final Logger logger = LoggerFactory.getLogger(EtcdUtil.class);
    public final String ETCD_SERVER_ADDRESS = "http://192.168.10.01:2379,http://192.168.10.02:2379,http://192.168.10.03:2379";
    public final String ETCD_SERVER_PATH = "/v2/keys";

    List<InetSocketAddress> clientList = Lists.newArrayList();
    List<String> serverList = Lists.newArrayList();

    public EtcdUtil() {
        List<String> servers = Arrays.asList(ETCD_SERVER_ADDRESS.split(","));
        for (int i = 0; i < servers.size(); i++) {
            try {
                RemotingUrl remotingUrl = URLUtil.parseHostAndPort(servers.get(i));
                InetSocketAddress address = new InetSocketAddress(remotingUrl.getHost(), remotingUrl.getPort());
                clientList.add(address);
                serverList.add(servers.get(i));
            } catch (Exception e) {
                logger.error("init EtcdUtil fail,e ={}", e.getMessage());
            }
        }
    }

    public InetSocketAddress getInetSocketAddress() {
        Random random = new Random();
        Integer randomPos = random.nextInt(clientList.size());
        return clientList.get(randomPos);
    }

    public QueryStringEncoder getQueryStringEncoder(String etcdServerNode) {
        String url = null;
        try {
            Random random = new Random();
            Integer randomPos = random.nextInt(serverList.size());
            url = serverList.get(randomPos) + ETCD_SERVER_PATH + etcdServerNode;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new QueryStringEncoder(url);
    }

}
