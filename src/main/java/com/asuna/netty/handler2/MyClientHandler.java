package com.asuna.netty.handler2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.nio.charset.Charset;

/**
 * 客户端自己的业务处理器，服务器给他发消息时会调用的方法
 */
public class MyClientHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private int count;
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        byte[] buffer = new byte[msg.readableBytes()];
        String message = new String(buffer, Charset.forName("utf-8"));
        System.out.println("client recv:" + message);
        System.out.println("client count" + ++count);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        ctx.close();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        for (int i = 0; i < 10; i++) {
            ByteBuf buffer = Unpooled.copiedBuffer("send from client:", Charset.forName("utf-8"));
            ctx.writeAndFlush(buffer);
        }
        ctx.writeAndFlush("active");
    }
}
