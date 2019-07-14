package com.asuna.netty.firstexample;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class TestServer {
    public static void main(String[] args) {
        /**
         * 新建两个线程组，bossGroup用于接收外面的新连接
         *               workerGroup用于处理新来的连接的业务，并返回数据
         */
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        /***
         * 启动服务，开启通道NioServerSocketChannel,通过反射的方式创建
         *         childHandler:子处理器，请求到来的处理器
         *         TestServerInitializer: 我们自己提供的初始化器
         */
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).
                childHandler(new TestServerInitializer());

        try {
            /**
             * 绑定端口号,并使用sync()方法进行启动
             */
            ChannelFuture channelFuture = serverBootstrap.bind(8899).sync();
            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
            //优雅关闭
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
