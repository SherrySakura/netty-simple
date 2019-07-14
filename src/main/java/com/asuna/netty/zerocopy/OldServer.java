package com.asuna.netty.zerocopy;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class OldServer {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(8999);

        while (true){
            Socket socket = serverSocket.accept();
            DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
            try {
                byte[] array = new byte[4096];
                while (true){
                    int readCount = dataInputStream.read(array, 0, args.length);
                    if (-1 == readCount){
                        break;
                    }
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
}
