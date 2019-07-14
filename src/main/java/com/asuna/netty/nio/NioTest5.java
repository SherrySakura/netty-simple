package com.asuna.netty.nio;

import java.nio.ByteBuffer;

public class NioTest5 {
    public static void main(String[] args) {
        ByteBuffer buffer = ByteBuffer.allocate(64);

        buffer.putInt(15);
        buffer.putLong(50000000L);
        buffer.putDouble(15.04525);
        buffer.putChar('a');
        buffer.putShort((short) 2);

        buffer.flip();

        System.out.println(buffer.getInt());
        System.out.println(buffer.getLong());
        System.out.println(buffer.getDouble());
        System.out.println(buffer.getChar());
        System.out.println(buffer.getShort());
    }
}
