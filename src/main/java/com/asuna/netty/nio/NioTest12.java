package com.asuna.netty.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class NioTest12 {
    public static void main(String[] args) throws IOException {
         int[] ports = new int[5];
         ports[0] = 5000;
         ports[1] = 5001;
         ports[2] = 5002;
         ports[3] = 5003;
         ports[4] = 5004;
         String str = "i am you 父亲";
         byte[] utf8 = str.getBytes("UTF-8");

        Selector selector = Selector.open();
        for (int i = 0; i < ports.length; i++) {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);

            //获取和channel关联的socket
            ServerSocket socket = serverSocketChannel.socket();
            InetSocketAddress address = new InetSocketAddress(ports[i]);
            socket.bind(address);

            //必须选择为accept作为option，当有客户端向服务器发起连接时，服务器会获取连接
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        }

        while (true){
            int numbers = selector.select();
            System.out.println("numbers:" + numbers);
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            System.out.println("selectedKeys" + selectionKeys);
            Iterator<SelectionKey> iterator = selectionKeys.iterator();

            while (iterator.hasNext()){
                SelectionKey selectionKey = iterator.next();

                //监听是否有连接进来
                if (selectionKey.isAcceptable()){
                    ServerSocketChannel channel = (ServerSocketChannel) selectionKey.channel();
                    SocketChannel socketChannel = channel.accept();
                    socketChannel.configureBlocking(false);
                    socketChannel.register(selector, SelectionKey.OP_READ);

                    //必须要删除，否则还会继续监听这个连接事件，而这个连接已经建立了
                    iterator.remove();

                    //接着监听是否有数据可以读取
                }else if (selectionKey.isReadable()){
                    SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
                    int bytesRead = 0;
                    while (true){
                        ByteBuffer buffer = ByteBuffer.allocate(512);
                        int read = socketChannel.read(buffer);
                        if (read <= 0){
                            break;
                        }
                        buffer.flip();
                        socketChannel.write(buffer);
                        bytesRead += read;
                    }
                    iterator.remove();
                }
            }
        }
    }
}
