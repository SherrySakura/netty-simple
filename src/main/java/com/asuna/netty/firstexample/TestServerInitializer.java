package com.asuna.netty.firstexample;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;

public class TestServerInitializer extends ChannelInitializer<SocketChannel> {
    //连接管道
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        //里面有很多个拦截器
        ChannelPipeline pipeline = ch.pipeline();

        /**
         * 处理HTTP的重要组件
         * HttpServerCodec,TestHttpServerHandler不能为单例模式
         */
        pipeline.addLast("HttpServerCodec", new HttpServerCodec());
        pipeline.addLast("TestHttpServerHandler", new TestHttpServerHandler());

    }
}
