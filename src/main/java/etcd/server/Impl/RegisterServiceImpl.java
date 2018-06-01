package etcd.server.Impl;

import etcd.server.NettyConnectClient;
import etcd.server.RegisterService;
import etcd.server.util.EtcdUtil;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.*;
import mousio.client.retry.RetryWithExponentialBackOff;
import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.responses.EtcdAuthenticationException;
import mousio.etcd4j.responses.EtcdException;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class RegisterServiceImpl implements RegisterService {
    private static final Logger logger = LoggerFactory.getLogger(RegisterServiceImpl.class);
    public static final String REGISTER_VALUE_DATA = "{\"host\":\"%s\",\"weight\":%s,\"port\":%s}";
    //从配置文件里面获取
    public final String ETCD_SERVER_ADDRESS = "http://192.168.10.01:2379,http://192.168.10.02:2379,http://192.168.10.03:2379";
    public final Integer TTL = 2;//second
    public final Integer HEATBEAT_INTERVIAL = 500;// ms
    private String host;
    private int port;

    private EtcdClient etcdClient;
    private String etcdServerHome;//服务注册dir
    private String etcdServerNode;//服务注册路径
    private NettyConnectClient nettyConnectClient;
    private EtcdUtil etcdUtil;

    public RegisterServiceImpl(String host, int port, String etcdRegisterPrefix) {
        etcdUtil = new EtcdUtil();
        nettyConnectClient = new NettyConnectClientFactory();
        nettyConnectClient.init();
        assert etcdRegisterPrefix != null;
        this.host = host;
        this.port = port;
        this.etcdServerHome = etcdRegisterPrefix;
        this.etcdServerNode = etcdServerHome + "/" + host + ":" + port;
        List<String> servers = Arrays.asList(ETCD_SERVER_ADDRESS.split(","));
        final URI[] etcdServers = new URI[servers.size()];
        for (int i = 0; i < servers.size(); i++) {
            etcdServers[i] = URI.create(servers.get(i));
        }
        etcdClient = new EtcdClient(etcdServers);
        // Set the retry policy for all requests on a etcd client connection
        // Will retry with an interval of 200ms with timeout of a total of 20000ms
        etcdClient.setRetryHandler(new RetryWithExponentialBackOff(20, 4, 10000));
    }

    /**
     * 注册服务心跳检测
     */
    @Override
    public void register() {
        this.submitEtcdCluster();
        new Thread(new GardEtcd()).start();
    }

    /**
     * 注册服务
     */
    public void submitEtcdCluster() {
        logger.info("start register server http(s)://{}:{} to {} success.", host, port, etcdServerNode);
        Throwable throwable = null;
        for (int i = 0; i < 3; i++) {//循环重试
            try {
                etcdClient.put(etcdServerNode, String.format(REGISTER_VALUE_DATA, host, 5, port)).prevExist(false).ttl(5)
                        .timeout(3, TimeUnit.SECONDS)
                        .send().get();
                logger.info("register to {} success.", etcdServerNode);
                return;
            } catch (IOException e) {
                throwable = e;
            } catch (EtcdException e) {
                e.printStackTrace();
            } catch (TimeoutException e) {
                e.printStackTrace();
            } catch (EtcdAuthenticationException e) {
                e.printStackTrace();
            }
            sleep(1000);
        }
        //注册失败
        if (throwable != null) {
            logger.error("register fail.", throwable);
        }

    }

    /**
     * 下线服务
     */
    public void unregister() {
        logger.info("unregister the server from etcd. node={}", etcdServerNode);
        for (int i = 0; i < 3; i++) {
            try {
                etcdClient.delete(etcdServerNode).send().get();
                break;
            } catch (IOException e) {
                logger.warn("unregister fail.", e);
            } catch (EtcdException e) {
                logger.warn("unregister fail.", e);
            } catch (EtcdAuthenticationException e) {
                logger.warn("unregister fail.", e);
                break;
            } catch (TimeoutException e) {
                logger.warn("unregister fail.", e);
            }
            sleep(1000);
        }
        sleep(2000);
        logger.info("unregister the server from etcd success. node={}", etcdServerNode);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
        }
    }

    /**
     * 心跳刷新
     *
     * @throws IOException
     */
    private void refresh() throws IOException {
        Channel channel = nettyConnectClient.getChannel();
        if (channel == null) return;
        URI uri = null;
        try {
            QueryStringEncoder encoder = etcdUtil.getQueryStringEncoder(etcdServerNode);
            encoder.addParam("value", String.format(REGISTER_VALUE_DATA, host, 5, port));
            encoder.addParam("ttl", String.valueOf(TTL));
            uri = new URI(encoder.toString());
        } catch (Exception e) {
            logger.error("etcd URI request  cause exception,e={}", e.getMessage());
        }
        HttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, uri.toASCIIString());
        httpRequest.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        httpRequest.headers().set(HttpHeaderNames.HOST, uri.getHost());
        channel.writeAndFlush(httpRequest);
        nettyConnectClient.releaseChannel(channel);
    }

    /**
     * 启用线程定时刷新连接--心跳机制
     */
    private class GardEtcd implements Runnable {
        @Override
        public void run() {
            Thread.currentThread().setUncaughtExceptionHandler(new ExceptionHandler());
            while (true) {
                try {
                    Thread.sleep(HEATBEAT_INTERVIAL);
                    refresh();
                } catch (Exception e) {
                    logger.error("refresh service error,it is {}", ExceptionUtils.getFullStackTrace(e));
                }
            }
        }
    }

    /**
     * 使用UncaughtExceptionHandler重启线程
     */
    private class ExceptionHandler implements Thread.UncaughtExceptionHandler {
        @Override
        public void uncaughtException(Thread t, Throwable e) {
            logger.warn("refresh thread cause exception, e ={}", ExceptionUtils.getFullStackTrace(e));
            new Thread(new GardEtcd()).start();
            logger.info("start a new Thread success.");
        }
    }


}
