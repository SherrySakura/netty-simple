package com.asuna.netty.handler3;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.nio.charset.Charset;

public class MyClientHandler extends SimpleChannelInboundHandler<PersonProtocol> {
    private int count;
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, PersonProtocol msg) throws Exception {
        int length = msg.getLength();
        byte[] content = msg.getContent();
        System.out.println("client recv:");
        System.out.println("length:" + length);
        System.out.println("content:" + new String(content, Charset.forName("utf-8")));

        System.out.println("count:" + (++count));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        for (int i = 0; i < 10; i++) {
            String messageToBeSend = "send from client";
            byte[] content = messageToBeSend.getBytes("utf-8");
            int length = messageToBeSend.getBytes("utf-8").length;
            PersonProtocol protocol = new PersonProtocol();
            protocol.setContent(content);
            protocol.setLength(length);
            ctx.writeAndFlush(protocol);
        }
    }
}
