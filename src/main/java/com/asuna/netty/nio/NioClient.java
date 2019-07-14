package com.asuna.netty.nio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NioClient {
    public static void main(String[] args) throws IOException {
        try {
            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            Selector selector = Selector.open();
            socketChannel.register(selector, SelectionKey.OP_CONNECT);
            socketChannel.connect(new InetSocketAddress("127.0.0.1", 8899));

            while (true){
                selector.select();
                Set<SelectionKey> keys = selector.selectedKeys();

                for (SelectionKey key :
                        keys) {
                    if (key.isConnectable()){
                        SocketChannel client = (SocketChannel) key.channel();

                        //连接操作是否处于正在进行的状态
                        if (client.isConnectionPending()){
                            //连接真正建立好，与服务器建立好了双向的数据传输通道
                            client.finishConnect();
                            ByteBuffer writeBuffer = ByteBuffer.allocate(1024);
                            writeBuffer.put((LocalDateTime.now() + "连接成功").getBytes());
                            writeBuffer.flip();
                            client.write(writeBuffer);

                            //使用线程池，进行再另一个辅线程中进行监听键盘的输入，并把该输入放进ByteBuffer中
                            ExecutorService executorService = Executors.newSingleThreadExecutor(Executors.defaultThreadFactory());
                            executorService.submit(() -> {
                                while (true){
                                    try {
                                        writeBuffer.clear();
                                        InputStreamReader input = new InputStreamReader(System.in);
                                        BufferedReader br = new BufferedReader(input);
                                        String sendMessage = br.readLine();
                                        writeBuffer.put(sendMessage.getBytes());
                                        writeBuffer.flip();
                                        client.write(writeBuffer);
                                    }catch (Exception e){
                                        e.printStackTrace();
                                    }
                                }
                            });
                        }

                        client.register(selector, SelectionKey.OP_READ);
                    }else if (key.isReadable()){
                        SocketChannel client = (SocketChannel) key.channel();
                        ByteBuffer readBuffer = ByteBuffer.allocate(1024);
                        int count = client.read(readBuffer);
                        if (count > 0){
                            String recrviedMessage = new String(readBuffer.array(), 0, count);
                            System.out.println(recrviedMessage);
                        }
                    }
                }
                keys.clear();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
