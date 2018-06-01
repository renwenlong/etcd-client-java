package etcd.server;

import io.netty.channel.Channel;

public interface NettyConnectClient {
    void init();

    Channel getChannel();

    void releaseChannel(Channel channel);
}
