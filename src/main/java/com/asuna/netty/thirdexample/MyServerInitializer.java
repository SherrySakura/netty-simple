package com.asuna.netty.thirdexample;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

public class MyServerInitializer extends ChannelInitializer<SocketChannel> {

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        //netty自带的读写空闲处理器
        pipeline.addLast(new IdleStateHandler(5,7,10, TimeUnit.SECONDS));
        pipeline.addLast(new MyServerHandler());
    }
}
