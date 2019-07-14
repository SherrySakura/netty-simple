package com.asuna.netty.zerocopy;

import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.Socket;

public class OldClient {
    public static void main(String[] args) throws Exception {
        Socket socket = new Socket("localhost", 8999);
        String fileName = "E:\\暗黑破坏神III\\Data\\data\\data.001";
        InputStream inputStream = new FileInputStream(fileName);
        DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());

        byte[] buffer = new byte[4096];
        long readCount = 0;
        long total = 0;

        long startTime = System.currentTimeMillis();

        while ((readCount = inputStream.read(buffer)) >= 0){
            total += readCount;
            dataOutputStream.write(buffer);
        }
        System.out.println("发送总字节数:" + total + "，耗时：" + (System.currentTimeMillis() - startTime));

        dataOutputStream.close();
        socket.close();
        inputStream.close();
    }
}
