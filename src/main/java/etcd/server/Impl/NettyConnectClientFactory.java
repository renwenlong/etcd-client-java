package etcd.server.Impl;

import etcd.server.NettyConnectClient;
import etcd.server.netty.EtcdChannelPoolHandler;
import etcd.server.util.EtcdUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.ChannelPoolMap;
import io.netty.channel.pool.FixedChannelPool;
import io.netty.channel.pool.SimpleChannelPool;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class NettyConnectClientFactory implements NettyConnectClient {
    private static final Logger logger = LoggerFactory.getLogger(NettyConnectClientFactory.class);

    public static final AttributeKey<SimpleChannelPool> CHANNEL_POOL_KEY = AttributeKey.valueOf("etcd.channel.pool");

    private final EventLoopGroup group = new NioEventLoopGroup();
    private final Bootstrap bootstrap = new Bootstrap();
    private ChannelPoolMap<InetSocketAddress, FixedChannelPool> poolMap;
    private EtcdUtil etcdUtil;

    @Override
    public void init() {
        etcdUtil = new EtcdUtil();
        bootstrap.group(group).channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true);

        poolMap = new AbstractChannelPoolMap<InetSocketAddress, FixedChannelPool>() {
            @Override
            protected FixedChannelPool newPool(InetSocketAddress key) {
                bootstrap.remoteAddress(key);
                return new FixedChannelPool(bootstrap, new EtcdChannelPoolHandler(), 20);
            }
        };
    }

    @Override
    public Channel getChannel() {
        try {
            SimpleChannelPool channelPool = poolMap.get(etcdUtil.getInetSocketAddress());
            Future<Channel> channelFuture = channelPool.acquire();
            Channel channel = channelFuture.get(1000, TimeUnit.MILLISECONDS);
            Attribute<SimpleChannelPool> attr = channel.attr(CHANNEL_POOL_KEY);
            attr.set(channelPool);
            return channel;
        } catch (Exception e) {
            logger.error("getChannel fail,e={}", ExceptionUtils.getFullStackTrace(e));
        }
        return null;
    }

    @Override
    public void releaseChannel(Channel channel) {
        if (channel != null) {
            Attribute<SimpleChannelPool> attr = channel.attr(CHANNEL_POOL_KEY);
            SimpleChannelPool channelPool = attr.get();
            channelPool.release(channel);
        }
    }
}
