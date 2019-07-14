package com.asuna.netty.bytebuf;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class ByteBufTest0 {
    public static void main(String[] args) {
        ByteBuf buf = Unpooled.buffer(10);
        for (int i = 0; i < 10; i++) {
            buf.writeByte(i);
        }

        for (int i = 0; i < buf.capacity(); i++) {
            System.out.println(buf.getByte(i));
        }
    }
}
