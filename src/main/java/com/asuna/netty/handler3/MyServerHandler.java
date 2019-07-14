package com.asuna.netty.handler3;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.nio.charset.Charset;
import java.util.UUID;

public class MyServerHandler extends SimpleChannelInboundHandler<PersonProtocol> {
    private int count;
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, PersonProtocol msg) throws Exception {
        int length = msg.getLength();
        byte[] content = msg.getContent();

        System.out.println("server recv:");
        System.out.println("length:" + length);
        System.out.println("content:" + new String(content, Charset.forName("utf-8")));

        System.out.println("count:" + (++count));

        String response = UUID.randomUUID().toString();
        int resLenth = response.getBytes("utf-8").length;
        byte[] resContent = response.getBytes("utf-8");

        PersonProtocol protocol = new PersonProtocol();
        protocol.setLength(resLenth);
        protocol.setContent(resContent);

        ctx.writeAndFlush(protocol);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
