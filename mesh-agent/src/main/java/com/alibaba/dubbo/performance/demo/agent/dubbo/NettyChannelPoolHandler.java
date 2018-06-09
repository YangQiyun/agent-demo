package com.alibaba.dubbo.performance.demo.agent.dubbo;


import com.alibaba.dubbo.performance.demo.agent.HelloController;
import io.netty.channel.Channel;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.channel.socket.SocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class NettyChannelPoolHandler implements ChannelPoolHandler {

    private Logger logger = LoggerFactory.getLogger(HelloController.class);

    @Override
    public void channelReleased(Channel ch) throws Exception {
        //logger.info("channelReleased. Channel ID: " + ch.id());
        //System.out.println("channelReleased. Channel ID: " + ch.id());
    }
    @Override
    public void channelAcquired(Channel ch) throws Exception {
        //logger.info("channelAcquired. Channel ID: " + ch.id());
    }
    @Override
    public void channelCreated(Channel ch) throws Exception {
        //logger.info("channelCreated. Channel ID: " + ch.id());
        SocketChannel channel = (SocketChannel) ch;
        channel.config().setKeepAlive(true);
        channel.config().setTcpNoDelay(true);
        channel.pipeline()
                .addLast(new RpcClientInitializer());
    }
}