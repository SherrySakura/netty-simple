package com.asuna.netty.handler3;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class MyPeronEncoder extends MessageToByteEncoder<PersonProtocol> {
    @Override
    protected void encode(ChannelHandlerContext ctx, PersonProtocol msg, ByteBuf out) throws Exception {
        out.writeInt(msg.getLength());
        out.writeBytes(msg.getContent());
    }
}
