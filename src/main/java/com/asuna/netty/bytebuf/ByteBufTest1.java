package com.asuna.netty.bytebuf;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.Charset;

public class ByteBufTest1 {
    public static void main(String[] args) {
        ByteBuf byteBuf = Unpooled.copiedBuffer("hello world", Charset.forName("UTF-8"));
        if (byteBuf.hasArray()){
            byte[] content = byteBuf.array();
            System.out.println(new String(content, Charset.forName("UTF-8")));
            System.out.println(byteBuf.arrayOffset());
            System.out.println(byteBuf.readerIndex());
            System.out.println(byteBuf.writerIndex());
            System.out.println(byteBuf.readableBytes());
        }
    }
}
