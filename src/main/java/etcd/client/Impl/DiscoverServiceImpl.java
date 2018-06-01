package etcd.client.Impl;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Ints;
import etcd.client.DiscoverService;
import etcd.server.bean.ServerInstance;
import mousio.client.promises.ResponsePromise;
import mousio.client.retry.RetryWithExponentialBackOff;
import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.promises.EtcdResponsePromise;
import mousio.etcd4j.responses.EtcdErrorCode;
import mousio.etcd4j.responses.EtcdException;
import mousio.etcd4j.responses.EtcdKeysResponse;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.JsonClient;
import util.JsonUtil;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author wenlongren
 * @date 2018/5/30
 * @desc
 */
public class DiscoverServiceImpl implements DiscoverService {
    private static final Logger logger = LoggerFactory.getLogger(DiscoverServiceImpl.class);
    public static final Integer ADD_INDEX_DETAL = 1;
    public static final String CREATE_ACTION = "create";
    public static final String SET_ACTION = "set";
    public static final String EXPIRE_ACTION = "expire";
    private EtcdClient etcdClient;
    //从配置文件里面获取
    public final String ETCD_SERVER_ADDRESS = "http://192.168.10.01:2379,http://192.168.10.02:2379,http://192.168.10.03:2379";
    public final String ETCD_SERVER_PREFIX = "/engine-servers";
    JsonClient jsonClient = JsonUtil.getClient();
    NewsRecDao newsRecDao = NewsRecDaoUtil.getInstance();//高可用备份可用服务实例(redis做实例备份)

    Map<String, ServerInstance> concurrentMap = Maps.newConcurrentMap();

    public DiscoverServiceImpl() {
        List<String> servers = Arrays.asList(ETCD_SERVER_ADDRESS.split(","));
        final URI[] etcdServers = new URI[servers.size()];
        for (int i = 0; i < servers.size(); i++) {
            etcdServers[i] = URI.create(servers.get(i));
        }
        etcdClient = new EtcdClient(etcdServers);
        etcdClient.setRetryHandler(new RetryWithExponentialBackOff(20, 4, 10000));
        initExistServers();
    }

    //代理重启初始化已存在的可用实例
    private void initExistServers() {
        for (int i = 0; i < 3; i++) {//循环重试
            try {
                List<EtcdKeysResponse.EtcdNode> nodes = etcdClient.get(ETCD_SERVER_PREFIX).recursive().sorted().timeout(3, TimeUnit.SECONDS).send().get().node.nodes;
                for (EtcdKeysResponse.EtcdNode node : nodes) {
                    ServerInstance severInstance = jsonClient.fromJson(node.value, ServerInstance.class);
                    if (severInstance == null) continue;
                    logger.info("init available server [{}]", severInstance.toString());
                    concurrentMap.put(node.key, severInstance);
                }
                if (MapUtils.isNotEmpty(concurrentMap)) {
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // 保证高可用,若注册中心集群宕机，从redis备份中取出可用实例
        if (MapUtils.isEmpty(concurrentMap)) {
            logger.info("Maybe etcd cluster has been breakdown !");
            List<ServerInstance> availableServers = newsRecDao.getAvailableServers(ETCD_SERVER_PREFIX);
            if (CollectionUtils.isEmpty(availableServers)) return;
            for (ServerInstance server : availableServers) {
                String key = ETCD_SERVER_PREFIX + "/" + server.getHost() + ":" + server.getPort();
                concurrentMap.put(key, server);
            }
        }
    }

    @Override
    public void startWatch() {
        try {
            watchChange();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<ServerInstance> getAvailableServers() {
        List<ServerInstance> serverList = Lists.newArrayList();
        for (ServerInstance server : concurrentMap.values()) {
            serverList.add(server);
        }
        return serverList;
    }

    private void watchChange() throws IOException {
        EtcdResponsePromise<EtcdKeysResponse> send = etcdClient.get(ETCD_SERVER_PREFIX).waitForChange().dir().recursive().send();
        send.addListener(new ResponsePromise.IsSimplePromiseResponseHandler<EtcdKeysResponse>() {
            @Override
            public void onResponse(ResponsePromise<EtcdKeysResponse> response) {
                Throwable t = response.getException();
                if (t instanceof EtcdException) {
                    if (((EtcdException) t).isErrorCode(EtcdErrorCode.NodeExist)) {
                        logger.error("onResponse cause exception,it is {}", ExceptionUtils.getFullStackTrace((t)));
                    }
                }
                // getNow() returns null on exception
                EtcdKeysResponse now = response.getNow();
                if (response != null && now != null) {
                    try {
                        String key = now.node.key;
                        if (CREATE_ACTION.equals(now.action.toString())) {
                            ServerInstance severInstance = jsonClient.fromJson(now.node.value, ServerInstance.class);
                            if (severInstance != null) {
                                concurrentMap.put(key, severInstance);
                                newsRecDao.setAvailableServer(ETCD_SERVER_PREFIX, severInstance);
                                logger.info("register server is [{}], etcd action is  [{}]", severInstance.toString(), now.action.toString());
                            }
                        }
                        if (SET_ACTION.equals(now.action.toString())) {
                            ServerInstance severInstance = jsonClient.fromJson(now.node.value, ServerInstance.class);
                            if (severInstance != null) {
                                concurrentMap.put(key, severInstance);
                            }
                        }
                        if (EXPIRE_ACTION.equals(now.action.toString())) {//过期不返回 value
                            String address = key.substring(key.lastIndexOf("/") + ADD_INDEX_DETAL, key.length());
                            concurrentMap.remove(key);
                            ServerInstance serverInstance = getServerInstance(address);
                            newsRecDao.delOneAvailableServer(ETCD_SERVER_PREFIX, serverInstance);
                            logger.info("unregister server is [{}], etcd action is  [{}]", address, now.action.toString());
                        }
                    } catch (Exception e) {
                        logger.error("watchChange cause exception ex is {}", ExceptionUtils.getFullStackTrace(e));
                    }
                }
                try {
                    watchChange();
                } catch (Exception e) {
                    logger.error("watchChange cause exception, ex is {}", ExceptionUtils.getFullStackTrace(e));
                }
            }

            private ServerInstance getServerInstance(String address) {
                ServerInstance server = new ServerInstance();
                try {
                    String[] addArr = address.split(":");
                    server.setHost(addArr[0]);
                    server.setPort(Ints.tryParse(addArr[1]));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return server;
            }
        });
    }

}