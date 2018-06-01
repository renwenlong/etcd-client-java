package etcd.server.netty;

import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EtcdChannelPoolHandler implements ChannelPoolHandler {
    private static final Logger logger = LoggerFactory.getLogger(EtcdChannelPoolHandler.class);
    @Override
    public void channelReleased(Channel ch) throws Exception {
        logger.debug("channelReleased. Channel ID: " + ch.id());
    }

    @Override
    public void channelAcquired(Channel ch) throws Exception {
        logger.debug("channelAcquired. Channel ID: " + ch.id());
    }

    @Override
    public void channelCreated(Channel ch) throws Exception {
        logger.debug("newsRec channelCreated. Channel ID: " + ch.id());
        SocketChannel channel = (SocketChannel) ch;
        channel.config().setKeepAlive(true);
        channel.config().setTcpNoDelay(true);
        channel.config().setReuseAddress(true);
        channel.config().setAllocator(UnpooledByteBufAllocator.DEFAULT);
        channel.pipeline()
                .addLast(new HttpClientCodec())
                .addLast(new HttpObjectAggregator(65535))
                .addLast(new EtcdServerHandler());
    }
}
