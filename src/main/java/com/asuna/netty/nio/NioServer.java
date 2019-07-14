package com.asuna.netty.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class NioServer {

    private static Map<String, SocketChannel> clientMap = new HashMap<>();
    public static void main(String[] args) throws IOException {
        //样板代码，每一个NIO程序都会这四行代码
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        ServerSocket serverSocket = serverSocketChannel.socket();
        serverSocket.bind(new InetSocketAddress(8899));

        Selector selector = Selector.open();

        //最开始一定是注册为accept事件
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        while (true){
            try {
                //默认为阻塞方法，直到有一个事件发生
                selector.select();

                //当上一个方法释放阻塞时，就可以获取到与之关联的key 的集合
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                selectionKeys.forEach(selectionKey -> {
                    final SocketChannel client;
                    if (selectionKey.isAcceptable()){
                        //肯定返回ServerSocketChannel对象，因为上面只注册了ServerSocketChannel到accept事件上
                        ServerSocketChannel channel = (ServerSocketChannel) selectionKey.channel();
                        try {
                            client = channel.accept();
                            client.configureBlocking(false);
                            client.register(selector, SelectionKey.OP_READ);

                            //将该客户端存入map中，以便之后进行消息分发
                            String key = "[" + UUID.randomUUID().toString() + "]";
                            clientMap.put(key, client);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }else if (selectionKey.isReadable()){
                        client = (SocketChannel) selectionKey.channel();
                        ByteBuffer readBuffer = ByteBuffer.allocate(1024);

                        try {
                            int count = client.read(readBuffer);
                            if (count > 0){
                                readBuffer.flip();

                                //注意编码消息
                                Charset charset = Charset.forName("UTF-8");
                                String receviedMessage = String.valueOf(charset.decode(readBuffer).array());
                                System.out.println(client + ": " + receviedMessage);
                                String senderKey = "";

                                //两轮遍历，先获取到发送端的key，再用一轮循环获取到值
                                for (Map.Entry<String, SocketChannel> entry :
                                        clientMap.entrySet()) {
                                    if (client == entry.getValue()){
                                        senderKey = entry.getKey();
                                        break;
                                    }
                                }
                                for (Map.Entry<String, SocketChannel> entry :
                                        clientMap.entrySet()) {
                                    SocketChannel value = entry.getValue();
                                    ByteBuffer writeBuffer = ByteBuffer.allocate(1024);
                                    writeBuffer.put((senderKey + ": " + receviedMessage).getBytes());
                                    writeBuffer.flip();
                                    value.write(writeBuffer);
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }


                });
                selectionKeys.clear();
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
}
