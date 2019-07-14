package com.asuna.netty.nio;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

public class NioTest10 {
    public static void main(String[] args) throws IOException {
        RandomAccessFile randomAccessFile = new RandomAccessFile("NioTest10.txt", "rw");
        FileChannel fileChannel = randomAccessFile.getChannel();
        FileLock lock = fileChannel.lock(3, 6, true);
        System.out.println("valid:" + lock.isValid());
        System.out.println("lock type:" + lock.isShared());
        lock.release();
        randomAccessFile.close();
    }
}
