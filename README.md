[TOC]



# Netty

## 利用netty搭建基础的hello worldHTTP协议的服务器

### 在IDEA中新建gradle工程

在工程的build.gradle文件中写入依赖项

```groovy
plugins {
    id 'java'
}

group 'com.asuna.netty'
version '1.0'

sourceCompatibility = 1.8

repositories {
    mavenCentral()
}

dependencies {
    // https://mvnrepository.com/artifact/io.netty/netty-all
    compile group: 'io.netty', name: 'netty-all', version: '4.1.32.Final'
}
```

### 创建主服务类，内部包含有main函数，作为启动入口

类名：==TestServer==

```java
package com.asuna.netty.firstexample;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class TestServer {
    public static void main(String[] args) {
        /**
         * 新建两个线程组，bossGroup用于接收外面的新连接
         *               workerGroup用于处理新来的连接的业务，并返回数据
         */
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        /***
         * 启动服务，开启通道NioServerSocketChannel,通过反射的方式创建
         *         childHandler:子处理器，请求到来的处理器
         *         TestServerInitializer: 我们自己提供的初始化器
         */
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).
                childHandler(new TestServerInitializer());

        try {
            /**
             * 绑定端口号,并使用sync()方法进行启动
             */
            ChannelFuture channelFuture = serverBootstrap.bind(8899).sync();
            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
            //优雅关闭
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}

```

### 创建服务初始化器

类名：==TestServerInitializer==

```java
package com.asuna.netty.firstexample;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;

public class TestServerInitializer extends ChannelInitializer<SocketChannel> {
    //连接管道
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        //里面有很多个拦截器
        ChannelPipeline pipeline = ch.pipeline();

        /**
         * 处理HTTP的重要组件
         * HttpServerCodec,TestHttpServerHandler不能为单例模式
         */
        pipeline.addLast("HttpServerCodec", new HttpServerCodec());
        pipeline.addLast("TestHttpServerHandler", new TestHttpServerHandler());

    }
}

```

### 创建自己的HTTP服务处理器

类名：==TestHttpServerHandler==

```java
package com.asuna.netty.firstexample;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

/**
 * 自己的HTTP处理器，负责处理逻辑
 */
public class TestHttpServerHandler extends SimpleChannelInboundHandler<HttpObject> {

    //处理请求，构造响应在这个方法中完成
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (msg instanceof HttpRequest){
            //要返回给客户端的内容
            ByteBuf content = Unpooled.copiedBuffer("Hello world", CharsetUtil.UTF_8);

            //构建一个响应，灿哥参数分别为：
            //1. HTTP协议
            //2. HTTP状态
            //3. 返回内容
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());

            //一定要write和flush一起使用，不然会把响应放进缓冲区，flush之后才会写回客户端
            ctx.writeAndFlush(response);
        }
    }
}

```

### 流程组件分析

目前仅能收到localhost:8899的请求，无法对地址进行路由，而且在浏览器中请求localhost:8899会产生两个请求：

1. 主体请求，即localhost:8899
2. 请求一个图表文件：localhost:8899/favicon.ico

使用curl命令工具不会产生两个请求，由于没有对请求方法进行判别，理论上任何方法都能进入到channelRead0方法。

组件调用顺序逻辑

```handler added```先被调用，添加自己的处理器

```channel registered```接着调用通道注册

```channel active```请求到来，通道被激活

```channel inactive``` 请求处理完成，需要断开连接，通道变为不活跃

```channel unregistered``` 通道被注销

注意，如果请求协议为HTTP1.1 会有一个keep-alive字段，因此不会有后两个方法执行，通道不会被关闭。根据keep-alive约定的时间去关闭连接，且由服务端来关闭。

若在自定义的处理器最后调用一次

```java
ctx.channel().close();
```

那不管是否由keep-alive字段，请求结束之后都会主动关闭连接，注销通道。

上述代码中的msg的实际类型为

```class io.netty.handler.codec.http.DefaultHttpRequest```和

```class io.netty.handler.codec.http.LastHttpContent$1``` 且这个为内部类，其实这两个类均实现了```HttpRequest```类

```HttpServerCodec``` 融合了两种编解码器

1. 请求的解码：```HttpRequestDecoder```
2. 响应的编码：```HttpResponseEncoder```

## 建立一个简单的客户端服务端的Socket通信项目

整个项目目录结构如下所示

![avatar](./image/secondsampleprojectstructure.bmp)

### 服务端编写

同样编写如下三个组件：

1. MySever
2. MyServerInitializer
3. MyServerHandler

针对于1，同样需要两个事件循环组，一个用于接收新的连接，一个用于处理新来的连接的业务

```MySever``` 类如下

```java
package com.asuna.netty.secondsample;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class MySever {
    public static void main(String[] args) {
        /**
         * 新建两个线程组，bossGroup用于接收外面的新连接
         *               workerGroup用于处理新来的连接的业务，并返回数据
         * 如果使用handler那么会针对于bossGroup进行处理
         * 使用childHandler会针对于workerGroup进行处理
         */
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                    .childHandler(new MyServerInitializer());
            ChannelFuture channelFuture = serverBootstrap.bind(8899).sync();
            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}

```

```MyServerInitializer``` 类如下：注意本类中使用的是```SocketChannel```泛型而不是之前的HTTP的泛型

```java
package com.asuna.netty.secondsample;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

public class MyServerInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        /**
         * LengthFieldBasedFrameDecoder: 字符长度编解码器
         * LengthFieldPrepender:
         * StringDecoder，StringEncoder：字符串编解码器
         *
         */
        pipeline.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4,0 ,4));
        pipeline.addLast(new LengthFieldPrepender(4));
        pipeline.addLast(new StringDecoder(CharsetUtil.UTF_8));
        pipeline.addLast(new StringEncoder(CharsetUtil.UTF_8));
        pipeline.addLast(new MyServerHandler());
    }
}

```

```MyServerHandler``` 类如下：处理业务逻辑等。

```java
package com.asuna.netty.secondsample;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.UUID;

public class MyServerHandler extends SimpleChannelInboundHandler<String> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
        System.out.println(ctx.channel().remoteAddress() + ", " + msg);
        ctx.channel().writeAndFlush("from server: " + UUID.randomUUID());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        ctx.close();
    }
}

```

### 客户端编写

客户端要比服务端少一个时间循环组，因为他是主动连接服务器。同样也是三个组件

1. MyClient
2. MyClientInitializer
3. MyClientHandler

```MyClient``` 类如下：

```java
package com.asuna.netty.secondsample;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

public class MyClient {
    public static void main(String[] args) {
        EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(eventLoopGroup).channel(NioSocketChannel.class)
                    .handler(new MyClientInitializer());
            ChannelFuture channelFuture = bootstrap.connect("localhost", 8899).sync();
            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            eventLoopGroup.shutdownGracefully();
        }
    }
}

```

```MyClientInitializer``` 类如下：

```java
package com.asuna.netty.secondsample;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

public class MyClientInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        /**
         * LengthFieldBasedFrameDecoder: 字符长度编解码器
         * LengthFieldPrepender:
         * StringDecoder，StringEncoder：字符串编解码器
         *
         */
        pipeline.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4,0 ,4));
        pipeline.addLast(new LengthFieldPrepender(4));
        pipeline.addLast(new StringDecoder(CharsetUtil.UTF_8));
        pipeline.addLast(new StringEncoder(CharsetUtil.UTF_8));
        pipeline.addLast(new MyClientHandler());
    }
}
```

```MyClientHandler``` 类如下：

```java
package com.asuna.netty.secondsample;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.time.LocalDateTime;

/**
 * 客户端自己的业务处理器，服务器给他发消息时会调用的方法
 */
public class MyClientHandler extends SimpleChannelInboundHandler<String> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
        System.out.println(ctx.channel().remoteAddress());
        System.out.println("client output" + msg);
        ctx.writeAndFlush("from client:" + LocalDateTime.now());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        ctx.close();
    }
}
```

## netty读写机制和长连接要素

### 服务器端配置代码

需要三个组件：

1. MySever
2. MyServerInitializer
3. MyServerHandler

和原来一样，基本配置如下：

```java
package com.asuna.netty.secondsample;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class MySever {
    public static void main(String[] args) {
        /**
         * 新建两个线程组，bossGroup用于接收外面的新连接
         *               workerGroup用于处理新来的连接的业务，并返回数据
         * 如果使用handler那么会针对于bossGroup进行处理
         * 使用childHandler会针对于workerGroup进行处理
         */
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                    .childHandler(new MyServerInitializer());
            ChannelFuture channelFuture = serverBootstrap.bind(8899).sync();
            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
```

```IdleStateHandler```：心跳检测，在没有检查到读写时间的时候触发该时间

在构造函数中，有四个参数可以设置：

1. ```readerIdleTime```：读取空闲时间，多长时间没有读取
2. ```writerIdleTime```：多长时间没有写入
3. ```allIdleTime```：多长时间没有读取+写入
4. ```unit```：时间单位，默认为秒

在服务端的initializer中，加入新的netty自带的IdleStateHandler，可以处理读写空闲的事件：

```java
package com.asuna.netty.thirdexample;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

public class MyServerInitializer extends ChannelInitializer<SocketChannel> {

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        //netty自带的读写空闲处理器
        pipeline.addLast(new IdleStateHandler(5,7,10, TimeUnit.SECONDS));
        pipeline.addLast(new MyServerHandler());
    }
}

```

自己的处理器如下：

```java
package com.asuna.netty.thirdexample;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;

public class MyServerHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent){
            IdleStateEvent event = (IdleStateEvent) evt;
            String eventType = null;
            switch (event.state()){
                case READER_IDLE:
                    eventType = "读空闲";
                    break;
                case WRITER_IDLE:
                    eventType = "写空闲";
                    break;
                case ALL_IDLE:
                    eventType = "读写空闲";
                    break;
            }
            System.out.println(ctx.channel().remoteAddress() + "-超时事件" + eventType);
        }
    }
}


```

多用于APP于服务端的长连接，维持一个心跳包，如果客户端断网，服务端是不知道的，需要一个心跳包来维持连接，超时之后将自动断开连接

## IO体系架构回顾与装饰器的具体应用

Java自带两种类型的io架构：最为传统的位于```java.io```包下的bio，即阻塞式io，还有一种在jdk1.4被提出来的nio。即非阻塞式io。

java.io包中主要分为两大类：字节流和字符流，字符流是基于字节流的，只是针对于字符做了一些简化

Byte Streams：根类------> InputStream/OutputStream

读数据的逻辑为：

1. 打开一个流
2. 用一个while循环，不断读取流中的数据
3. 关闭流

写数据的逻辑为：

1. 打开一个流
2. 在while循环中不断写
3. 关闭流

一个stream不可能既是输入流又是输出流。

### 流的分类

==节点流==：从特定的地方读写的流类，如，从磁盘或一块内存区域

==过滤流==：使用节点流作为输入或者输出。过滤流是使用一个已经存在的输入流或输出流连接创建的

### inputstream类层次结构

![avatar](/image/inputstream.jpg)

常用的方法有：

1. 读数据read()
2. 获取输入字节流数available()
3. 定位输入指针的方法skip(), reset(), mark()

### outputstream类层次结构

![avatar](/image/outputstream.jpg)

### Java IO库使用装饰器模式进行设计

![avatar](/image/decorator.jpg)

![avatar](/image/decorator2.jpg)

#### 装饰模式的角色

1. 抽象构建角色（Component）：给出一个接口，以规范准备接受附加责任的对象----```InputStream```
2. 具体构件角色（Concrete Component）：定义一个将要接收附加责任的类---```FileInputStream```
3. 装饰角色（Decorator）：持有一个构件对象的引用，并定义一个与抽象构件接口一致的接口
4. 具体装饰角色（Concrete Decorator）：负责给构件对象贴上附加的责任---```BufferedInputStream```

#### 特点

![avatar](/image/decoratorCh.jpg)

#### 装饰模式的适用性

![avatar](/image/decoratorSuit.jpg)

## NIO体系分析

### 一个小例子，用nio方式写入并读取随机数

```java
package com.asuna.netty.nio;

import java.nio.IntBuffer;
import java.security.SecureRandom;

public class NioTest1 {
    public static void main(String[] args) {
        IntBuffer buffer = IntBuffer.allocate(10);
        for (int i = 0; i < buffer.capacity(); i++) {
            int random = new SecureRandom().nextInt(20);
            buffer.put(random);
        }

        buffer.flip();//实现读写状态的翻转，读写切换之前必须执行该方法

        while (buffer.hasRemaining()){
            System.out.println(buffer.get());
        }
    }
}

```

### nio中的一些重要概念

java.io中最为核心的一个概念是流（Stream），面向流的编程。java中，一个流要么是输入流要么是输出流，不能同时为两个流

java.nio中，有三个核心概念，面向块（block）或缓冲区（buffer）编程，buffer在底层实现上，本身就是一块内存，实际上是一个数组。==数据的读写都是通过buffer实现的==。nio中存在一个channel既是输入流又是输出流，即该buffer既可读也可写。

1. Selector
2. Channel
3. Buffer

三者结构如下

![avatar](/image/niostructure.jpg)

除了数组之外，buffer还提供了对于数据的结构化访问方式，并且可以追踪到系统的读写过程。

Java中的==7==中原生数据类型都有各自对应的buffer类型，如IntBuffer，LongBuffer等，这些均继承自Buffer类。没有BooleanBuffer类型

Channel指的是可以向其写入数据或是从中读取数据的对象，类似于java.io中的Stream，所有数据的读写都是通过buffer来进行的，永远不会出现直接从channel读取或者写入数据的情况。

与Stream不同的是，channel是双向的。能更好的反映出底层操作系统的真实情况；在Linux系统中，底层操作系统的通道就是双向的。

### 第二个小例子，用nio的方式读取一个文件内容，并输出

```java
package com.asuna.netty.nio;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class NioTest2 {
    public static void main(String[] args) throws IOException {
        FileInputStream fileInputStream = new FileInputStream("NioTest2.txt");
        FileChannel fileChannel = fileInputStream.getChannel();
        ByteBuffer byteBuffer = ByteBuffer.allocate(512);
        fileChannel.read(byteBuffer);
        byteBuffer.flip();
        while (byteBuffer.hasRemaining()){
            byte b = byteBuffer.get();
            System.out.println("Ch:" + (char)b);
        }
        fileInputStream.close();
    }
}

```

### 第三个小例子，向一个文件中写入指定数组内的数据

```java
package com.asuna.netty.nio;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class NioTest3 {
    public static void main(String[] args) throws IOException {
        FileOutputStream fileOutputStream = new FileOutputStream("NioTest3.txt");
        FileChannel fileChannel = fileOutputStream.getChannel();
        ByteBuffer byteBuffer = ByteBuffer.allocate(512);
        byte[] message = "hello world".getBytes();
        for (int i = 0; i < message.length; i++) {
            byteBuffer.put(message[i]);
        }
        byteBuffer.flip();
        fileChannel.write(byteBuffer);
        fileOutputStream.close();
    }
}

```

注意到，buffer中的flip()方法：

```java
/**
     * Flips this buffer.  The limit is set to the current position and then
     * the position is set to zero.  If the mark is defined then it is
     * discarded.
     *
     * <p> After a sequence of channel-read or <i>put</i> operations, invoke
     * this method to prepare for a sequence of channel-write or relative
     * <i>get</i> operations.  For example:
     *
     * <blockquote><pre>
     * buf.put(magic);    // Prepend header
     * in.read(buf);      // Read data into rest of buffer
     * buf.flip();        // Flip buffer
     * out.write(buf);    // Write header + data to channel</pre></blockquote>
     *
     * <p> This method is often used in conjunction with the {@link
     * java.nio.ByteBuffer#compact compact} method when transferring data from
     * one place to another.  </p>
     *
     * @return  This buffer
     */
    public final Buffer flip() {
        limit = position;
        position = 0;
        mark = -1;
        return this;
    }
```

## Buffer中各重要状态属性的含义与关系图解

### 关于NIO Buffer中的3个重要状态属性的含义：position，limit，capacity

==capacity==：包含的个数，不可能为负数和变化

==limit==：不会超过它的capacity值，能够读写的范围

==position==：下一个要读或者要写的位置，永远不会超过limit

![avatar](/image/buffer_attr.jpg)

 在flip()操作后，会将position指向最开始的位置，且将limit之前指向的位置变为指向之前position的位置。capacity位置不变。

![avatar](/image/buffer_attr2.jpg)

> 多次调用flip()之后，limit将越来越小，需要clear()将三个属性重置

## Java NIO核心类源码解析

每一个子类有两类操作：

1. relative操作，get/set。一次读取或写入多个元素从当前位置开始，并增加position指针。
2. absolute操作，get/set。接收一个显式的元素的索引，不会影响position的值。

mark：在调用reset操作时，必须提前定义好mark，否则有异常。

> 0 <= mark <= position <= limit <= capacity

==clear()==：重置三个属性

```java
/**
     * Clears this buffer.  The position is set to zero, the limit is set to
     * the capacity, and the mark is discarded.
     *
     * <p> Invoke this method before using a sequence of channel-read or
     * <i>put</i> operations to fill this buffer.  For example:
     *
     * <blockquote><pre>
     * buf.clear();     // Prepare buffer for reading
     * in.read(buf);    // Read data</pre></blockquote>
     *
     * <p> This method does not actually erase the data in the buffer, but it
     * is named as if it did because it will most often be used in situations
     * in which that might as well be the case. </p>
     *
     * @return  This buffer
     */
    public final Buffer clear() {
        position = 0;
        limit = capacity;
        mark = -1;
        return this;
    }
```

==rewind()==：重新读，limit不变，将position设置为0

```java
/**
     * Rewinds this buffer.  The position is set to zero and the mark is
     * discarded.
     *
     * <p> Invoke this method before a sequence of channel-write or <i>get</i>
     * operations, assuming that the limit has already been set
     * appropriately.  For example:
     *
     * <blockquote><pre>
     * out.write(buf);    // Write remaining data
     * buf.rewind();      // Rewind buffer
     * buf.get(array);    // Copy data into array</pre></blockquote>
     *
     * @return  This buffer
     */
    public final Buffer rewind() {
        position = 0;
        mark = -1;
        return this;
    }
```

> 如果一个方法不需要返回值，则会返回对象本身，因此可以实现链式调用

当调用allocate()方法时，会将position设置为0，limit和capacity一致，并初始化一个capacity大小的数组。

```java
// Creates a new buffer with the given mark, position, limit, capacity,
    // backing array, and array offset
    //
    IntBuffer(int mark, int pos, int lim, int cap,   // package-private
                 int[] hb, int offset)
    {
        super(mark, pos, lim, cap);
        this.hb = hb;
        this.offset = offset;
    }
```

get和put方法，在各个类型的Buffer中均以抽象方法存在，具体在HeapXXXBuffer中实现

==put方法==

```java
public IntBuffer put(int x) {
        hb[ix(nextPutIndex())] = x;
        return this;
    }

    public IntBuffer put(int i, int x) {
        hb[ix(checkIndex(i))] = x;
        return this;
    }

    public IntBuffer put(int[] src, int offset, int length) {
        checkBounds(offset, length, src.length);
        if (length > remaining())
            throw new BufferOverflowException();
        System.arraycopy(src, offset, hb, ix(position()), length);
        position(position() + length);
        return this
    }

    public IntBuffer put(IntBuffer src) {
        if (src instanceof HeapIntBuffer) {
            if (src == this)
                throw new IllegalArgumentException();
            HeapIntBuffer sb = (HeapIntBuffer)src;
            int n = sb.remaining();
            if (n > remaining())
                throw new BufferOverflowException();
            System.arraycopy(sb.hb, sb.ix(sb.position()),
                             hb, ix(position()), n);
            sb.position(sb.position() + n);
            position(position() + n);
        } else if (src.isDirect()) {
            int n = src.remaining();
            if (n > remaining())
                throw new BufferOverflowException();
            src.get(hb, ix(position()), n);
            position(position() + n);
        } else {
            super.put(src);
        }
        return this;
    }
```

==get方法==

```java
protected int ix(int i) {
        return i + offset;
    }

    public int get() {
        return hb[ix(nextGetIndex())];
    }

    public int get(int i) {
        return hb[ix(checkIndex(i))];
    }

    public IntBuffer get(int[] dst, int offset, int length) {
        checkBounds(offset, length, dst.length);
        if (length > remaining())
            throw new BufferUnderflowException();
        System.arraycopy(hb, ix(position()), dst, offset, length);
        position(position() + length);
        return this;
    }
```

==compact方法==：

1. 将所有未读的数据复制到buffer的起始位置处 
2. 将position设为最后一个未读元素的后面
3. 将limit设置为capacity
4. 现在buffer准备好了，但是不会覆盖未读的数据

```java
public ByteBuffer compact() {

        System.arraycopy(hb, ix(position()), hb, ix(0), remaining());
        position(remaining());
        limit(capacity());
        discardMark();
        return this;
    }
```



## 文件通道用法

### 第四个小例子，读取一个文件的内容，将其写到另一个文件中

```java
package com.asuna.netty.nio;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class NioTest4 {
    public static void main(String[] args) throws IOException {
        FileInputStream inputStream = new FileInputStream("input.txt");
        FileOutputStream outputStream = new FileOutputStream("output.txt");
        FileChannel inputChannel = inputStream.getChannel();
        FileChannel outputChannel = outputStream.getChannel();

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        while (true){
            buffer.clear();
            int read = inputChannel.read(buffer);
            System.out.println("read:" + read);
            if (-1 == read){
                break;
            }
            buffer.flip();
            outputChannel.write(buffer);
        }
        inputChannel.close();
        outputChannel.close();
    }
}

```

如果将第19行代码```buffer.clear()```注释，将会在读完后一直写入文件中。在第一次写完文件后，```position=limit```，且limit就为刚才读取到的字节数（因为```flip()```）。此时没有空余的位置可以读入，因为```position```不可能大于```limit```，因此```read()```返回了0，不会退出循环，而再次调用```flip()```后，```position```又为0了，且```limit```还是不变，因此又会把第一次读到的数据全部再次写入文件中，造成死循环。只有调用了```buffer.clear()```将```limit```重新置为```capacity```，```position```置为0，就可以再次继续读取。

### 从NIO读取文件的三个步骤

1. 从FileInputStream获取到FileChannel对象；
2. 创建Buffer；
3. 将数据从Channel读取到Buffer中

从NIO写文件与上述类似，只是变为FileOutputStream

### 绝对方法与相对方法的含义：

1. 相对方法：limit值与position值会在操作时被考虑到
2. 绝对方法：完全忽略limit与position的值，。由用户指定索引位置

## Buffer深入讲解

ByteBuffer提供了一种类型化的put，可以根据Java的不同数据类型，进行直接put。例如```putChar()```

```java
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

```

取出来的类型顺序一定要和存放的顺序一致，否则可能会数据错乱或者报异常

### 第五个小例子，slice方法的使用

```java
package com.asuna.netty.nio;

import java.nio.ByteBuffer;

public class NioTest6 {
    public static void main(String[] args) {
        ByteBuffer buffer = ByteBuffer.allocate(10);

        for (int i = 0; i < buffer.capacity(); i++) {
            buffer.put((byte) i);
        }

        buffer.position(2);
        buffer.limit(6);

        ByteBuffer sliceBuffer = buffer.slice();
        for (int i = 0; i < sliceBuffer.capacity(); i++) {
            byte b = sliceBuffer.get(i);
            sliceBuffer.put(i, (byte) (2 * b));
        }

        buffer.position(0);
        buffer.limit(buffer.capacity());
        while (buffer.hasRemaining()){
            System.out.println(buffer.get());
        }
    }
}
```

在slice后返回的ByteBuffer若修改了其中的内容，原来的buffer也会变化，共享底层的数据数组。

### 第六个小例子，只读buffer

```java
package com.asuna.netty.nio;

import java.nio.ByteBuffer;

public class NioTest7 {
    public static void main(String[] args) {
        ByteBuffer buffer = ByteBuffer.allocate(10);
        System.out.println(buffer.getClass());
        for (int i = 0; i < buffer.capacity(); i++) {
            buffer.put((byte) i);
        }
        ByteBuffer readOnlyBuffer = buffer.asReadOnlyBuffer();
        System.out.println(readOnlyBuffer.getClass());
    }
}
```

12行```readOnlyBuffer```为只读的，调用他的修改数据的方法会直接抛出异常。

## NIO堆外内存与零拷贝

### DirectByteBuffer

```java
package com.asuna.netty.nio;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class NioTest8 {
    public static void main(String[] args) throws IOException {
        FileInputStream inputStream = new FileInputStream("input2.txt");
        FileOutputStream outputStream = new FileOutputStream("output2.txt");
        FileChannel inputChannel = inputStream.getChannel();
        FileChannel outputChannel = outputStream.getChannel();

        ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
        while (true){
            buffer.clear();
            int read = inputChannel.read(buffer);
            System.out.println("read:" + read);
            if (-1 == read){
                break;
            }
            buffer.flip();
            outputChannel.write(buffer);
        }
        inputChannel.close();
        outputChannel.close();
    }
}
```

DirectByteBuffer为数据存放于本地内存，但是DirectByteBuffer本身还是在Java堆中，因为DirectByteBuffer还是通过new的方式创建的

```java
public static ByteBuffer allocateDirect(int capacity) {
        return new DirectByteBuffer(capacity);
    }
```

数据数组是位于堆外内存（即操作系统内存）的，但是在DirectByteBuffer中必须要有一个能和堆外内存沟通的变量，在```Buffer```抽象类中，有一个```long address```变量负责与操作系统中的数据寻址。

```java
// Used only by direct buffers
    // NOTE: hoisted here for speed in JNI GetDirectBufferAddress
    long address;
```

指向堆外内存存放数据的地址。

![avatar](/image/directbytebyffer.jpg)

> 若用HeapByteBuffer会在堆内分配存放数据的内存，在读写的时候，又会在堆外分配一段内存，将堆中的数据copy到操作系统的内存区中，写入时会先将操作系统的内存区拷贝到堆中，再进行操作
>
> 若使用DirectByteBuffer则直接将数据存放到操作系统中的内存区中，不需要进行来回copy

若native方法直接操作Java堆中对象的话，由于native方法需要知道对象或者数据的精确地址才能操作，而堆中内存很可能因为Java的GC操作导致对象地址被改变（标记清除压缩算法），导致native方法无法正确找到对象地址。因此需要拷贝到堆外内存中。在copy过程中不会发生GC

## NIO中Scattering与Gathering分析

### MappedByteBuffer

为一个文件的内存映射，也是一个directbytebyffer，将一个物理磁盘中的文件映射到内存中，并且，该类也是直接操作操作系统的内存，实现了零拷贝。修改内存映射，即在内存中修改内容，之后会同步到物理磁盘中，但是这个同步不需要人为来操作。

```java
package com.asuna.netty.nio;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class NioTest9 {
    public static void main(String[] args) throws IOException {
        RandomAccessFile randomAccessFile = new RandomAccessFile("NioTest9.txt", "rw");
        FileChannel fileChannel = randomAccessFile.getChannel();
        MappedByteBuffer mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, 5);

        mappedByteBuffer.put(0, (byte) 'a');
        mappedByteBuffer.put(3, (byte) 'b');
        randomAccessFile.close();
    }
}

```

### 文件锁

可分为共享锁和排他锁：

1. 共享锁：都可以读
2. 排他锁：只能有一个写

```java
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
```

```FileLock```由```FileChannel```创建三个参数分别为：

1. 从哪个偏移量开始上锁；
2. 上锁的内容长度为多少；
3. 是否为共享锁，true---共享锁；false---排他锁

### 关于buffer的Scattering和Gathering

Scattering：将一个来自于一个channel的数据读取到多个buffer中，只有一个buffer读满了才会开始读下一个buffer；

Gathering：将第一个buffer的数据写到channel中，写完了再写第二个，接着第三个。。。。

#### Scattering举例

```java
package com.asuna.netty.nio;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

public class NioTest11 {
    public static void main(String[] args) throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        InetSocketAddress address = new InetSocketAddress(8899);
        serverSocketChannel.socket().bind(address);

        int messageLength = 2 + 3 + 4;
        ByteBuffer[] buffers = new ByteBuffer[3];
        buffers[0] = ByteBuffer.allocate(2);
        buffers[1] = ByteBuffer.allocate(3);
        buffers[2] = ByteBuffer.allocate(4);

        SocketChannel socketChannel = serverSocketChannel.accept();

        while (true){
            int bytesRead = 0;
            while (bytesRead < messageLength){
                long r = socketChannel.read(buffers);
                bytesRead += r;
                System.out.println("bytes read:" + bytesRead);
                Arrays.asList(buffers).stream().map(buffer -> "position:" + buffer.position() + ", limit:" + buffer.limit()).forEach(System.out::println);

            }
            Arrays.asList(buffers).forEach(buffer -> {
                buffer.flip();
            });
            long byteWriten = 0;
            while (byteWriten < messageLength){
                long r = socketChannel.write(buffers);
                byteWriten += r;
            }
            Arrays.asList(buffers).forEach(buffer -> {
                buffer.clear();
            });
            System.out.println("byteRead:" + bytesRead + ", byteWriten:" + byteWriten);
        }
    }
}

```

当收到正好9个字节时，会发现三个buffer刚刚好读满，若不足9个字节，那么会优先将第一个buffer读满，接着再读入第二个。若超过9个，先读满9个，执行while外面的操作，再接着读超过的部分。

## Selector源码分析

可以通过调用此类的方法来创建选择器，该方法将使用系统的默认值来创建新的选择器。 还可以通过调用自定义选择器提供程序的方法来创建选择器。 选择器保持打开状态，直到通过其方法关闭。

通道可以注册到selector上，通过selection key来区别

selection key表示一系列连接时间（连接进来，是否可以读，是否可以写），有三大类：

1. ```key set```当前注册到该选择器上的所有通道。通过```keys()```返回
2. ```selected-key set```每一个通道被侦测到已经准备好操作的时候，key会变为selected-key set
3. ```cancelled-key set```指的是那些没有被注销但是却已经不再关注的通道集合

## NIO网络访问模式分析

通过channel的register方法将channel注册到selector上，这个方法的调用会导致一个事件：会把一个key添加到集合当中。cancelled key被在选择时被删，在下一次选择动作（调用```select()```方法，其本身就是一个阻塞方法）中被注销。

key被添加到cancelled key的方式：

1. 当该channel被关闭
2. 调用SelectionKey的cancel方法

移除key的方法：

1. 调用remove方法
2. 在获取了迭代器之后调用remove方法

selector的打开方式，由provider进行打开

```java
/**
     * Opens a selector.
     *
     * <p> The new selector is created by invoking the {@link
     * java.nio.channels.spi.SelectorProvider#openSelector openSelector} method
     * of the system-wide default {@link
     * java.nio.channels.spi.SelectorProvider} object.  </p>
     *
     * @return  A new selector
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    public static Selector open() throws IOException {
        return SelectorProvider.provider().openSelector();
    }

/**
     * Returns the system-wide default selector provider for this invocation of
     * the Java virtual machine.
     *
     * <p> The first invocation of this method locates the default provider
     * object as follows: </p>
     *
     * <ol>
     *
     *   <li><p> If the system property
     *   <tt>java.nio.channels.spi.SelectorProvider</tt> is defined then it is
     *   taken to be the fully-qualified name of a concrete provider class.
     *   The class is loaded and instantiated; if this process fails then an
     *   unspecified error is thrown.  </p></li>
     *
     *   <li><p> If a provider class has been installed in a jar file that is
     *   visible to the system class loader, and that jar file contains a
     *   provider-configuration file named
     *   <tt>java.nio.channels.spi.SelectorProvider</tt> in the resource
     *   directory <tt>META-INF/services</tt>, then the first class name
     *   specified in that file is taken.  The class is loaded and
     *   instantiated; if this process fails then an unspecified error is
     *   thrown.  </p></li>
     *
     *   <li><p> Finally, if no provider has been specified by any of the above
     *   means then the system-default provider class is instantiated and the
     *   result is returned.  </p></li>
     *
     * </ol>
     *
     * <p> Subsequent invocations of this method return the provider that was
     * returned by the first invocation.  </p>
     *
     * @return  The system-wide default selector provider
     */
    public static SelectorProvider provider() {
        synchronized (lock) {
            if (provider != null)
                return provider;
            return AccessController.doPrivileged(
                new PrivilegedAction<SelectorProvider>() {
                    public SelectorProvider run() {
                            if (loadProviderFromProperty())
                                return provider;
                            if (loadProviderAsService())
                                return provider;
                            provider = sun.nio.ch.DefaultSelectorProvider.create();
                            return provider;
                        }
                    });
        }
    }
```



### ServerSocketChannel

为一个可选择的面向流的监听socket，也是通过open方法进行打开，内部逻辑和selector类似。且必须经过```bind()```之后才能进行```accept()```操作，否则会抛出```NotYetBoundException```异常。支持一下几种option：

1. ```SO_RCVBUF```：socket接收缓冲区的大小
2. ```SO_REUSEADDR```：复用地址

并且要记住设置该channel为非阻塞模式

```java
/**
     * Adjusts this channel's blocking mode.
     *
     * <p> If the given blocking mode is different from the current blocking
     * mode then this method invokes the {@link #implConfigureBlocking
     * implConfigureBlocking} method, while holding the appropriate locks, in
     * order to change the mode.  </p>
     */
    public final SelectableChannel configureBlocking(boolean block)
        throws IOException
    {
        synchronized (regLock) {
            if (!isOpen())
                throw new ClosedChannelException();
            if (blocking == block)
                return this;
            if (block && haveValidKeys())
                throw new IllegalBlockingModeException();
            implConfigureBlocking(block);
            blocking = block;
        }
        return this;
    }
```

### 第七个小例子

```java
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

```

## 网络编程深度解析

完整的服务器端和客户端的编写---功能：多个客户端连接到服务端，任意一个客户端发送的消息可以在其他客户端上可见。

> 在每次处理完一个注册的事件之后都需要remove，不然会陷入死循环，或者把SelectionKey进行clear

### 服务端注意

需要保留所连接的客户端的连接信息（Mac信息），不然由于服务器就只有一个线程，监听一个端口，在处理完上一个客户端之后会把连接释放去处理下一个连接。不记录的话会导致无法推送到其他客户端。

服务端代码如下：

```java
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

```

客户端代码如下：客户端的逻辑和服务端类似，只是把accept改为connect，都需要注册读事件。

```java
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

```

## Java字符集编解码

### 第八个小例子，将一个文件读取并写入另一个文件，注意编解码

```java
package com.asuna.netty.nio;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

public class NioTest13 {
    public static void main(String[] args) throws IOException {
        String inputFile = "NioTest13_In.txt";
        String outputFile = "NioTest13_Out.txt";

        RandomAccessFile inputRandomAccessFile = new RandomAccessFile(inputFile, "r");
        RandomAccessFile outputRandomAccessFile = new RandomAccessFile(outputFile, "rw");

        long inputLength = new File(inputFile).length();

        FileChannel inputFileChannel = inputRandomAccessFile.getChannel();
        FileChannel outputFileChannel = outputRandomAccessFile.getChannel();
        MappedByteBuffer inputData = inputFileChannel.map(FileChannel.MapMode.READ_ONLY, 0, inputLength);

        //生成一个UTF-8的编解码器
        Charset charset = Charset.forName("UTF-8");
        
        //用UTF-8生成两个编码解码器
        CharsetDecoder decoder = charset.newDecoder();
        CharsetEncoder encoder = charset.newEncoder();

        CharBuffer charBuffer = decoder.decode(inputData);

        ByteBuffer outputData = encoder.encode(charBuffer);
        outputFileChannel.write(outputData);

        inputRandomAccessFile.close();
        outputRandomAccessFile.close();
    }
}

```

存储都是通过字节来表示的，字符只是编码的一种方式。

==ASCII== --- American Standard Code for Infomation Interchange, 美国信息交换标准代码

7bit 开表示一个字符---一共128个字符，对西方国家是完全够用的。后来不够用了，就出现了

==ISO-8859-1==编码，8bit表示一个字符，一共可以表示256个字符。但是对中文依然不够

==gb2312==：每一个汉字有一个特定的编码和它对应，用两个字节表示一个汉字，但是没有考虑生僻字的存在

==gbk==：对gb2312的扩展，加入了生僻字的编码

==gb18030==：最完整的汉字表示，是以上两种的超集

==big5==：繁体中文的编码

==Unicode==：所有的字符编码，最为广泛，采用两个字节表示一个字符 \uxxxx，但是对于英文的编码，会浪费空间

==utf==：Unicode translation format，Unicode是一种编码方式，而UTF是一种存储方式

==UTF-16LE（little ending），UTF-16BE（big ending）==：0xFEFF(BE)，0xFFFE(LE)。

==UTF-8==：变长字节表示形式，英文用一个字符，中文一般用三个字节表示，兼容ASCII和ISO-8859-1。最多用6个字节表示

==BOM==：byte order mark，字节序标记

用什么进行编码就应该用什么编码方式进行解码

## Netty源码分析准备

### 零拷贝深入分析

#### 原始的操作方式，非零拷贝

![avatar](/image/zerocopy1.jpg)

1. JVM发起一个```read()```调用，位于用户空间，向内核空间发送，操作系统会将用户空间模式切换到内核空间模式。
2. 在内核空间模式会向硬件发起数据读取请求。通过DMA将数据读取到内核空间数据缓冲区---==第一次拷贝==
3. 再将数据从内核空间缓冲区拷贝到用户空间的数据缓冲区---==第二次拷贝==
4. 执行业务逻辑代码
5. ```read()```操作结束
6. 接着JVM向操作系统发起```write()```调用，将数据拷贝回内核空间缓冲区，注意，和read操作时的缓冲区不一致。
7. 再由内核空间的数据缓冲区写入硬件，```write()```操作返回

一共有四次用户空间和内核空间的上下文切换，一共有两次数据拷贝

#### 零拷贝方式

![avatar](/image/zerocopy2.jpg)

1. JVM发起```sendFile()```系统调用
2. 接下来的操作均在内核空间进行。
3. 向硬件请求数据，并通过DMA读取到内核空间的数据缓冲区
4. 将数据写进将要发生的socket的数据缓冲区
5. 通过socket缓冲区直接发送数据到对方
6. 系统调用返回

只有两次上下文切换，且不会发生数据拷贝，但是还是存在一次DMA读取到的数据缓冲区到socket缓冲区的拷贝过程

#### 零拷贝方式2

![avatar](/image/zerocopy3.jpg)

需要硬件条件支持

如果这个时候需要干涉内核空间的操作的话需要使用内存映射文件，文件本身是映射到内核空间，但是应用程序可以直接访问到。

使用普通IO方式的服务端以及客户端代码

```java
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

```

```java
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

```

使用零拷贝的服务端与客户端代码

```java
package com.asuna.netty.zerocopy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class NewIOSever {
    public static void main(String[] args) throws IOException {
        InetSocketAddress address = new InetSocketAddress(8899);

        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        ServerSocket serverSocket = serverSocketChannel.socket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(address);

        ByteBuffer byteBuffer = ByteBuffer.allocate(4096);
        while (true){
            SocketChannel socketChannel = serverSocketChannel.accept();
            socketChannel.configureBlocking(true);
            int readCount = 0;
            while (-1 != readCount){
                try {
                    readCount = socketChannel.read(byteBuffer);
                }catch (Exception e){
                    e.printStackTrace();
                }
                byteBuffer.rewind();
            }
        }
    }
}

```

```java
package com.asuna.netty.zerocopy;

import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;

public class NewIOClient {
    public static void main(String[] args) throws Exception {
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.connect(new InetSocketAddress("localhost", 8899));
        socketChannel.configureBlocking(true);

        String fileName = "E:\\暗黑破坏神III\\Data\\data\\data.001";

        FileChannel fileChannel = new FileInputStream(fileName).getChannel();
        long startTime = System.currentTimeMillis();

        long transferCount = fileChannel.transferTo(0, fileChannel.size(), socketChannel);

        System.out.println("发送总字节数:" + transferCount + "， 耗时：" + (System.currentTimeMillis() - startTime));
    }
}

```

注意核心代码：```long transferCount = fileChannel.transferTo(0, fileChannel.size(), socketChannel);```，transferTo为实现了零拷贝的功能。

### Gathering操作对零拷贝的贡献

![avatar](/image/gather.jpg)

Gathering可以从多个缓冲区中读取数据，由于socket自带文件描述符，因此可以直接从内核缓冲区传送至socket缓冲区

## NIOEventLoopGroup分析

EventLoopGroup本身就是一个死循环，继承自EventExecutorGroup，是一个特殊的EventExecutorGroup，在事件循环过程中，可以注册通道channel，以备后续进行选择。

### API解释

- ```next()```：返回下一个事件循环

```java
/**
     * Return the next {@link EventLoop} to use
     */
    @Override
    EventLoop next();
```

- ```register(Channel channel)```：将一个channel注册到一个事件循环中，一个异步方法，在返回值中查看注册结果

```java
/**
     * Register a {@link Channel} with this {@link EventLoop}. The returned {@link ChannelFuture}
     * will get notified once the registration was complete.
     */
    ChannelFuture register(Channel channel);
```

- ```ChannelFuture register(ChannelPromise promise)```：注册完成后可以得到通知，ChannelPromise 对象里面包含有一个channel的引用

```java
/**
     * Register a {@link Channel} with this {@link EventLoop} using a {@link ChannelFuture}. The passed
     * {@link ChannelFuture} will get notified once the registration was complete and also will get returned.
     */
    ChannelFuture register(ChannelPromise promise);
```

NioEventLoopGroup基于NIO selector和channel的实现

如果构造方法不穿参数，则默认参数为0，

```java
/**
     * Create a new instance using the default number of threads, the default {@link ThreadFactory} and
     * the {@link SelectorProvider} which is returned by {@link SelectorProvider#provider()}.
     */
    public NioEventLoopGroup() {
        this(0);
    }
```

该构造方法会调用一下构造方法：

```java
/**
     * Create a new instance using the specified number of threads, {@link ThreadFactory} and the
     * {@link SelectorProvider} which is returned by {@link SelectorProvider#provider()}.
     */
    public NioEventLoopGroup(int nThreads) {
        this(nThreads, (Executor) null);
    }
```

接着，默认的executor为null，因为executor为null，所以会由Java NIO的SelectorProvider的provider方法提供一个

```java
public NioEventLoopGroup(int nThreads, Executor executor) {
        this(nThreads, executor, SelectorProvider.provider());
    }
```

接着，会传入一个DefaultSelectStrategyFactory实例

```java
public NioEventLoopGroup(
            int nThreads, Executor executor, final SelectorProvider selectorProvider) {
        this(nThreads, executor, selectorProvider, DefaultSelectStrategyFactory.INSTANCE);
    }
```

DefaultSelectStrategyFactory实例如下所示：返回

```java
package io.netty.channel;

/**
 * Factory which uses the default select strategy.
 */
public final class DefaultSelectStrategyFactory implements SelectStrategyFactory {
    public static final SelectStrategyFactory INSTANCE = new DefaultSelectStrategyFactory();

    private DefaultSelectStrategyFactory() { }

    @Override
    public SelectStrategy newSelectStrategy() {
        return DefaultSelectStrategy.INSTANCE;
    }
}
```

接着会调用如下构造函数：会提供一个默认的RejectedExecutionHandler，

```java
public NioEventLoopGroup(int nThreads, Executor executor, final SelectorProvider selectorProvider,
                             final SelectStrategyFactory selectStrategyFactory) {
        super(nThreads, executor, selectorProvider, selectStrategyFactory, RejectedExecutionHandlers.reject());
    }
```

接着会调用其父类的构造函数：会根据你的传递的线程数来确定需要初始化的线程总数

```java
/**
     * @see MultithreadEventExecutorGroup#MultithreadEventExecutorGroup(int, Executor,
     * EventExecutorChooserFactory, Object...)
     */
    protected MultithreadEventLoopGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory,
                                     Object... args) {
        super(nThreads == 0 ? DEFAULT_EVENT_LOOP_THREADS : nThreads, executor, chooserFactory, args);
    }
```

其中，DEFAULT_EVENT_LOOP_THREADS会根据查询当前的CPU核心数来决定有多少线程被初始化，核心代码：```NettyRuntime.availableProcessors() * 2```。

```java
private static final int DEFAULT_EVENT_LOOP_THREADS;

    static {
        DEFAULT_EVENT_LOOP_THREADS = Math.max(1, SystemPropertyUtil.getInt(
                "io.netty.eventLoopThreads", NettyRuntime.availableProcessors() * 2));

        if (logger.isDebugEnabled()) {
            logger.debug("-Dio.netty.eventLoopThreads: {}", DEFAULT_EVENT_LOOP_THREADS);
        }
    }
```

最终会调用到最根部的构造函数，位于MultithreadEventExecutorGroup类中，

```java
/**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param executor          the Executor to use, or {@code null} if the default should be used.
     * @param chooserFactory    the {@link EventExecutorChooserFactory} to use.
     * @param args              arguments which will passed to each {@link #newChild(Executor, Object...)} call
     */
    protected MultithreadEventExecutorGroup(int nThreads, Executor executor,
                                            EventExecutorChooserFactory chooserFactory, Object... args) {
        if (nThreads <= 0) {
            throw new IllegalArgumentException(String.format("nThreads: %d (expected: > 0)", nThreads));
        }

        if (executor == null) {
            executor = new ThreadPerTaskExecutor(newDefaultThreadFactory());
        }

        children = new EventExecutor[nThreads];

        for (int i = 0; i < nThreads; i ++) {
            boolean success = false;
            try {
                children[i] = newChild(executor, args);
                success = true;
            } catch (Exception e) {
                // TODO: Think about if this is a good exception type
                throw new IllegalStateException("failed to create a child event loop", e);
            } finally {
                if (!success) {
                    for (int j = 0; j < i; j ++) {
                        children[j].shutdownGracefully();
                    }

                    for (int j = 0; j < i; j ++) {
                        EventExecutor e = children[j];
                        try {
                            while (!e.isTerminated()) {
                                e.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
                            }
                        } catch (InterruptedException interrupted) {
                            // Let the caller handle the interruption.
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }

        chooser = chooserFactory.newChooser(children);

        final FutureListener<Object> terminationListener = new FutureListener<Object>() {
            @Override
            public void operationComplete(Future<Object> future) throws Exception {
                if (terminatedChildren.incrementAndGet() == children.length) {
                    terminationFuture.setSuccess(null);
                }
            }
        };

        for (EventExecutor e: children) {
            e.terminationFuture().addListener(terminationListener);
        }

        Set<EventExecutor> childrenSet = new LinkedHashSet<EventExecutor>(children.length);
        Collections.addAll(childrenSet, children);
        readonlyChildren = Collections.unmodifiableSet(childrenSet);
    }
```

并没有进行线程的开启，只是配置了相应的EventExecutor的初始化。其中需要关注核心类```Future```

## Executor实现机制及源码

```executor = new ThreadPerTaskExecutor(newDefaultThreadFactory());```

对于```newDefaultThreadFactory()```

线程的创建和线程要执行的任务是要进行分离的

```java
protected ThreadFactory newDefaultThreadFactory() {
        return new DefaultThreadFactory(getClass());
    }
```

生成一个默认的线程工厂。该线程工厂由netty提供，初始化线程并进行对线程的命名

```java
public DefaultThreadFactory(Class<?> poolType) {
        this(poolType, false, Thread.NORM_PRIORITY);
    }
```

```Thread.NORM_PRIORITY```为线程优先级，为Java在Thread类中定义。

```java
public DefaultThreadFactory(Class<?> poolType, boolean daemon, int priority) {
        this(toPoolName(poolType), daemon, priority);
    }
```

在上述方法中进行创建和对线程进行命名，第二个参数为是否为守护线程（后台线程）

将线程池的名字确定之后便调用下一构造方法：

```java
public DefaultThreadFactory(String poolName, boolean daemon, int priority, ThreadGroup threadGroup) {
        if (poolName == null) {
            throw new NullPointerException("poolName");
        }
        if (priority < Thread.MIN_PRIORITY || priority > Thread.MAX_PRIORITY) {
            throw new IllegalArgumentException(
                    "priority: " + priority + " (expected: Thread.MIN_PRIORITY <= priority <= Thread.MAX_PRIORITY)");
        }

        prefix = poolName + '-' + poolId.incrementAndGet() + '-';
        this.daemon = daemon;
        this.priority = priority;
        this.threadGroup = threadGroup;
    }
```

其中，线程组由一下代码决定：

```Thread.currentThread().getThreadGroup(): System.getSecurityManager().getThreadGroup()```

对于```ThreadPerTaskExecutor```

```java
public final class ThreadPerTaskExecutor implements Executor {
    private final ThreadFactory threadFactory;

    public ThreadPerTaskExecutor(ThreadFactory threadFactory) {
        if (threadFactory == null) {
            throw new NullPointerException("threadFactory");
        }
        this.threadFactory = threadFactory;
    }

    @Override
    public void execute(Runnable command) {
        threadFactory.newThread(command).start();
    }
}
```

将上一个方法创建的线程工厂传入并赋值，当调用execute时，会启动线程。

重点接口：```Executor```

会执行所提交过来的runnable，将任务的提交和任务的运行解耦了。包括线程使用的细节，线程的调度等等。不会严格地要求所执行的任务是异步的。执行器可以立即在调用者线程中执行任务

```java
class DirectExecutor implements Executor {
    public void execute(Runnable r) {
      r.run();
    }
}}
```

直接调用```r.run()```，并没有创建新的线程，会直接在本线程中启动

组合执行器：执行一系列的任务（Runable数组）

```java
class SerialExecutor implements Executor {
 *   final Queue<Runnable> tasks = new ArrayDeque<Runnable>();
 *   final Executor executor;
 *   Runnable active;
 *
 *   SerialExecutor(Executor executor) {
 *     this.executor = executor;
 *   }
 *
 *   public synchronized void execute(final Runnable r) {
 *     tasks.offer(new Runnable() {
 *       public void run() {
 *         try {
 *           r.run();
 *         } finally {
 *           scheduleNext();
 *         }
 *       }
 *     });
 *     if (active == null) {
 *       scheduleNext();
 *     }
 *   }
 *
 *   protected synchronized void scheduleNext() {
 *     if ((active = tasks.poll()) != null) {
 *       executor.execute(active);
 *     }
 *   }
 * }}
```

会不断调用execute方法，直到任务队列中没有任务

## 服务端初始化过程和反射在其中的运用

==服务端代码==：```ServerBootstrap serverBootstrap = new ServerBootstrap();```

```ServerBootstrap```：是```ServerChannel```的子类，而且```ServerChannel```仅仅为一个标记接口，其继承于```Channel```类，所有的功能都在```Channel```内完成。

```java
/**
 * A {@link Channel} that accepts an incoming connection attempt and creates
 * its child {@link Channel}s by accepting them.  {@link ServerSocketChannel} is
 * a good example.
 */
public interface ServerChannel extends Channel {
    // This is a tag interface.
}
```

接收对端发来的连接请求，并创建相应的Child Channel，通过ServerBootstrap 可以很容易的创建ServerChannel。

构造方法仅仅创建一个实例

```java
public ServerBootstrap() { }
```

服务端代码：```serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                    .childHandler(new MyServerInitializer());```

1. ```group()```方法：，parentGroup处理连接请求，childGroup负责处理任务，且为方法链编程风格。

```java
public ServerBootstrap group(EventLoopGroup parentGroup, EventLoopGroup childGroup) {
        super.group(parentGroup);
        if (childGroup == null) {
            throw new NullPointerException("childGroup");
        }
        if (this.childGroup != null) {
            throw new IllegalStateException("childGroup set already");
        }
        this.childGroup = childGroup;
        return this;
    }
```

第2行调用父类的构造方法，```AbstractBootstrap```为父类，

```java
public abstract class AbstractBootstrap<B extends AbstractBootstrap<B, C>, C extends Channel> implements Cloneable

/**
     * The {@link EventLoopGroup} which is used to handle all the events for the to-be-created
     * {@link Channel}
     */
    public B group(EventLoopGroup group) {
        if (group == null) {
            throw new NullPointerException("group");
        }
        if (this.group != null) {
            throw new IllegalStateException("group set already");
        }
        this.group = group;
        return self();
    }
    
@SuppressWarnings("unchecked")
    private B self() {
        return (B) this;
    }
```

他有一个泛型B，约束为AbstractBootstrap的子类，返回一个ServerBootstrap对象。

2. ```channel(Class<? extends C> channelClass)```

```java
/**
     * The {@link Class} which is used to create {@link Channel} instances from.
     * You either use this or {@link #channelFactory(io.netty.channel.ChannelFactory)} if your
     * {@link Channel} implementation has no no-args constructor.
     */
    public B channel(Class<? extends C> channelClass) {
        if (channelClass == null) {
            throw new NullPointerException("channelClass");
        }
        return channelFactory(new ReflectiveChannelFactory<C>(channelClass));
    }
```

```C```为```ServerChannel```，创建Channel实例。第10行中的ReflectiveChannelFactory方法，通过反射的方式调用它的构造方法去创建一个新的实例。

```java
/**
 * A {@link ChannelFactory} that instantiates a new {@link Channel} by invoking its default constructor reflectively.
 */
public class ReflectiveChannelFactory<T extends Channel> implements ChannelFactory<T> {

    private final Class<? extends T> clazz;

    public ReflectiveChannelFactory(Class<? extends T> clazz) {
        if (clazz == null) {
            throw new NullPointerException("clazz");
        }
        //对成员变量赋值
        this.clazz = clazz;
    }

    @Override
    public T newChannel() {
        try {
            return clazz.getConstructor().newInstance();
        } catch (Throwable t) {
            throw new ChannelException("Unable to create Channel from class " + clazz, t);
        }
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(clazz) + ".class";
    }
}
```

```java
/**
     * {@link io.netty.channel.ChannelFactory} which is used to create {@link Channel} instances from
     * when calling {@link #bind()}. This method is usually only used if {@link #channel(Class)}
     * is not working for you because of some more complex needs. If your {@link Channel} implementation
     * has a no-args constructor, its highly recommend to just use {@link #channel(Class)} to
     * simplify your code.
     */
    @SuppressWarnings({ "unchecked", "deprecation" })
    public B channelFactory(io.netty.channel.ChannelFactory<? extends C> channelFactory) {
        return channelFactory((ChannelFactory<C>) channelFactory);
    }
```

通过该方法去创建一个复杂的实例，如果是需要调用无参的构造方法，则调用channel(Class)方法去创建实例。

对于服务端代码的NioServerSocketChannel，本质上就是一个selector

对于服务端代码的childHandler调用，

```java
/**
     * Set the {@link ChannelHandler} which is used to serve the request for the {@link Channel}'s.
     */
    public ServerBootstrap childHandler(ChannelHandler childHandler) {
        if (childHandler == null) {
            throw new NullPointerException("childHandler");
        }
        this.childHandler = childHandler;
        return this;
    }
```

将子处理器进行赋值。用于服务于针对于channel的请求。

到此为止，将ServerBootstrap的成员变量都配置好了。

在服务端的```bind()```方法进行启动

```java
/**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind(int inetPort) {
        return bind(new InetSocketAddress(inetPort));
    }
```

```java
/**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind(SocketAddress localAddress) {
        validate();
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }
        return doBind(localAddress);
    }
```

```java
private ChannelFuture doBind(final SocketAddress localAddress) {
        final ChannelFuture regFuture = initAndRegister();
        final Channel channel = regFuture.channel();
        if (regFuture.cause() != null) {
            return regFuture;
        }

        if (regFuture.isDone()) {
            // At this point we know that the registration was complete and successful.
            ChannelPromise promise = channel.newPromise();
            doBind0(regFuture, channel, localAddress, promise);
            return promise;
        } else {
            // Registration future is almost always fulfilled already, but just in case it's not.
            final PendingRegistrationPromise promise = new PendingRegistrationPromise(channel);
            regFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    Throwable cause = future.cause();
                    if (cause != null) {
                        // Registration on the EventLoop failed so fail the ChannelPromise directly to not cause an
                        // IllegalStateException once we try to access the EventLoop of the Channel.
                        promise.setFailure(cause);
                    } else {
                        // Registration was successful, so set the correct executor to use.
                        // See https://github.com/netty/netty/issues/2586
                        promise.registered();

                        doBind0(regFuture, channel, localAddress, promise);
                    }
                }
            });
            return promise;
        }
    }
```

注意到，所有方法均返回ChannelFuture

### Future\<V>

ChannelFuture继承自Future，Future\<V>代表异步计算的结果，它提供了一系列方法去检查计算是否完成，等待计算完成，并获取计算结果。当计算完成时只能通过get方法获取，若没完成，get方法会阻塞，通过cancel方法取消计算。若使用Future针对于取消型的任务，可以声明一个Future<?>并返回一个null作为计算结果。结果返回类型就为V

基本使用方式：

```java
interface ArchiveSearcher { String search(String target); }
 * class App {
 *   ExecutorService executor = ...
 *   ArchiveSearcher searcher = ...
 *   void showSearch(final String target)
 *       throws InterruptedException {
 *     Future<String> future
 *       = executor.submit(new Callable<String>() {
 *         public String call() {
 *             return searcher.search(target);
 *         }});
 *     displayOtherThings(); // do other things while searching
 *     try {
 *       displayText(future.get()); // use future
 *     } catch (ExecutionException ex) { cleanup(); return; }
 *   }
 * }}
```

netty中的Future方法增加了一些方法

1. ```addListener()```，监听Future的结果，一旦异步操作结束，listener会监听到结果，也可以添加多个监听器

```java
/**
     * Adds the specified listener to this future.  The
     * specified listener is notified when this future is
     * {@linkplain #isDone() done}.  If this future is already
     * completed, the specified listener is notified immediately.
     */
    Future<V> addListener(GenericFutureListener<? extends Future<? super V>> listener);
```

2. ```removeListener()```，移除监听器。

```java
/**
     * Removes the first occurrence of the specified listener from this future.
     * The specified listener is no longer notified when this
     * future is {@linkplain #isDone() done}.  If the specified
     * listener is not associated with this future, this method
     * does nothing and returns silently.
     */
    Future<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener);
```

3. ```sync()```，等待future完成。

```java
/**
     * Waits for this future until it is done, and rethrows the cause of the failure if this future
     * failed.
     */
    Future<V> sync() throws InterruptedException;
```

4. ```await()```，也是等待任务完成

```java
/**
     * Waits for this future to be completed.
     *
     * @throws InterruptedException
     *         if the current thread was interrupted
     */
    Future<V> await() throws InterruptedException;
```

5. ```getNow()```，立即得到执行结果，而不是以阻塞的方式获取。

```java
/**
     * Return the result without blocking. If the future is not done yet this will return {@code null}.
     *
     * As it is possible that a {@code null} value is used to mark the future as successful you also need to check
     * if the future is really done with {@link #isDone()} and not relay on the returned {@code null} value.
     */
    V getNow();
```

### ChannelFuture\<V>

ChannelFuture，重写了Future中的所有监听器的添加和删除方法。表示channel异步操作的结果。不同的结果所对应的方法返回值如下所示

```java
*                                      +---------------------------+
*                                      | Completed successfully    |
*                                      +---------------------------+
*                                 +---->      isDone() = true      |
* +--------------------------+    |    |   isSuccess() = true      |
* |        Uncompleted       |    |    +===========================+
* +--------------------------+    |    | Completed with failure    |
* |      isDone() = false    |    |    +---------------------------+
* |   isSuccess() = false    |----+---->      isDone() = true      |
* | isCancelled() = false    |    |    |       cause() = non-null  |
* |       cause() = null     |    |    +===========================+
* +--------------------------+    |    | Completed by cancellation |
*                                 |    +---------------------------+
*                                 +---->      isDone() = true      |
*                                      | isCancelled() = true      |
*                                      +---------------------------+
```



第1行：初始化和注册

```java
final ChannelFuture initAndRegister() {
        Channel channel = null;
        try {
            channel = channelFactory.newChannel();
            init(channel);
        } catch (Throwable t) {
            if (channel != null) {
                // channel can be null if newChannel crashed (eg SocketException("too many open files"))
                channel.unsafe().closeForcibly();
                // as the Channel is not registered yet we need to force the usage of the GlobalEventExecutor
                return new DefaultChannelPromise(channel, GlobalEventExecutor.INSTANCE).setFailure(t);
            }
            // as the Channel is not registered yet we need to force the usage of the GlobalEventExecutor
            return new DefaultChannelPromise(new FailedChannel(), GlobalEventExecutor.INSTANCE).setFailure(t);
        }

        ChannelFuture regFuture = config().group().register(channel);
        if (regFuture.cause() != null) {
            if (channel.isRegistered()) {
                channel.close();
            } else {
                channel.unsafe().closeForcibly();
            }
        }

        // If we are here and the promise is not failed, it's one of the following cases:
        // 1) If we attempted registration from the event loop, the registration has been completed at this point.
        //    i.e. It's safe to attempt bind() or connect() now because the channel has been registered.
        // 2) If we attempted registration from the other thread, the registration request has been successfully
        //    added to the event loop's task queue for later execution.
        //    i.e. It's safe to attempt bind() or connect() now:
        //         because bind() or connect() will be executed *after* the scheduled registration task is executed
        //         because register(), bind(), and connect() are all bound to the same thread.

        return regFuture;
    }
```

第4行，通过反射的方法创建Channel实例。**不允许在ChannelHandler中调用await方法**。ChannelHandler中的方法会被一个IO线程调用，await方法会阻塞方法并等待IO操作完成，因此会造成死锁。

反例：

```java
* // BAD - NEVER DO THIS
 * {@code @Override}
 * public void channelRead({@link ChannelHandlerContext} ctx, Object msg) {
 *     {@link ChannelFuture} future = ctx.channel().close();
 *     future.awaitUninterruptibly();
 *     // Perform post-closure operation
 *     // ...
 * }
```

正面例子：

```java
* // GOOD
 * {@code @Override}
 * public void channelRead({@link ChannelHandlerContext} ctx, Object msg) {
 *     {@link ChannelFuture} future = ctx.channel().close();
 *     future.addListener(new {@link ChannelFutureListener}() {
 *         public void operationComplete({@link ChannelFuture} future) {
 *             // Perform post-closure operation
 *             // ...
 *         }
 *     });
 * }
```

服务端启动的```bind()```详细：

```java
private ChannelFuture doBind(final SocketAddress localAddress) {
        final ChannelFuture regFuture = initAndRegister();
        final Channel channel = regFuture.channel();
        if (regFuture.cause() != null) {
            return regFuture;
        }

        if (regFuture.isDone()) {
            // At this point we know that the registration was complete and successful.
            ChannelPromise promise = channel.newPromise();
            doBind0(regFuture, channel, localAddress, promise);
            return promise;
        } else {
            // Registration future is almost always fulfilled already, but just in case it's not.
            final PendingRegistrationPromise promise = new PendingRegistrationPromise(channel);
            regFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    Throwable cause = future.cause();
                    if (cause != null) {
                        // Registration on the EventLoop failed so fail the ChannelPromise directly to not cause an
                        // IllegalStateException once we try to access the EventLoop of the Channel.
                        promise.setFailure(cause);
                    } else {
                        // Registration was successful, so set the correct executor to use.
                        // See https://github.com/netty/netty/issues/2586
                        promise.registered();

                        doBind0(regFuture, channel, localAddress, promise);
                    }
                }
            });
            return promise;
        }
    }
```

在该方法的第2行中的initAndRegister()里，有一个init()方法，在获取到了所创建的channel之后，会对这个channel进行handler的配置。

1. 对options进行设置
2. 对pipeline进行获取，并配置handler和ServerBootstrapAcceptor接收器。

```java
@Override
    void init(Channel channel) throws Exception {
        final Map<ChannelOption<?>, Object> options = options0();
        synchronized (options) {
            setChannelOptions(channel, options, logger);
        }

        final Map<AttributeKey<?>, Object> attrs = attrs0();
        synchronized (attrs) {
            for (Entry<AttributeKey<?>, Object> e: attrs.entrySet()) {
                @SuppressWarnings("unchecked")
                AttributeKey<Object> key = (AttributeKey<Object>) e.getKey();
                channel.attr(key).set(e.getValue());
            }
        }

        ChannelPipeline p = channel.pipeline();

        final EventLoopGroup currentChildGroup = childGroup;
        final ChannelHandler currentChildHandler = childHandler;
        final Entry<ChannelOption<?>, Object>[] currentChildOptions;
        final Entry<AttributeKey<?>, Object>[] currentChildAttrs;
        synchronized (childOptions) {
            currentChildOptions = childOptions.entrySet().toArray(newOptionArray(0));
        }
        synchronized (childAttrs) {
            currentChildAttrs = childAttrs.entrySet().toArray(newAttrArray(0));
        }

        p.addLast(new ChannelInitializer<Channel>() {
            @Override
            public void initChannel(final Channel ch) throws Exception {
                final ChannelPipeline pipeline = ch.pipeline();
                ChannelHandler handler = config.handler();
                if (handler != null) {
                    pipeline.addLast(handler);
                }

                ch.eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        pipeline.addLast(new ServerBootstrapAcceptor(
                                ch, currentChildGroup, currentChildHandler, currentChildOptions, currentChildAttrs));
                    }
                });
            }
        });
    }
```

上述代码中的第3-15行为填充对象的变量，包括设置options和attributes，第17行为获取到channel中的ChannelPipeline对象，可以允许用户自定义事件处理器。每一个channel都有其自己的pipeline。事件会被ChannelInboundHandler或者ChannelOutboundHandler处理器处理。当一个处理器处理完这个事件后会传递给最近的下一个处理器处理。

```java
*                                                 I/O Request
*                                            via {@link Channel} or
*                                        {@link ChannelHandlerContext}
*                                                      |
*  +---------------------------------------------------+---------------+
*  |                           ChannelPipeline         |               |
*  |                                                  \|/              |
*  |    +---------------------+            +-----------+----------+    |
*  |    | Inbound Handler  N  |            | Outbound Handler  1  |    |
*  |    +----------+----------+            +-----------+----------+    |
*  |              /|\                                  |               |
*  |               |                                  \|/              |
*  |    +----------+----------+            +-----------+----------+    |
*  |    | Inbound Handler N-1 |            | Outbound Handler  2  |    |
*  |    +----------+----------+            +-----------+----------+    |
*  |              /|\                                  .               |
*  |               .                                   .               |
*  | ChannelHandlerContext.fireIN_EVT() ChannelHandlerContext.OUT_EVT()|
*  |        [ method call]                       [method call]         |
*  |               .                                   .               |
*  |               .                                  \|/              |
*  |    +----------+----------+            +-----------+----------+    |
*  |    | Inbound Handler  2  |            | Outbound Handler M-1 |    |
*  |    +----------+----------+            +-----------+----------+    |
*  |              /|\                                  |               |
*  |               |                                  \|/              |
*  |    +----------+----------+            +-----------+----------+    |
*  |    | Inbound Handler  1  |            | Outbound Handler  M  |    |
*  |    +----------+----------+            +-----------+----------+    |
*  |              /|\                                  |               |
*  +---------------+-----------------------------------+---------------+
*                  |                                  \|/
*  +---------------+-----------------------------------+---------------+
*  |               |                                   |               |
*  |       [ Socket.read() ]                    [ Socket.write() ]     |
*  |                                                                   |
*  |  Netty Internal I/O Threads (Transport Implementation)            |
*  +-------------------------------------------------------------------+
```

netty会自己给我们添加一个ServerBootstrapAcceptor，专门负责处理新来的连接的接收事件。其中会尝试获取handler，也就是我们在server.java中的.handler方法，如果没有，则不会添加，有的话会在这里添加。

## Reactor模式---反应器模式

与之对应：Proactor模式。

经典的服务设计架构图：

![avatar](image/reactor_classic.jpg)

每一个handler都是在其自己的线程里面启动，为多线程模型。

![avatar](/image/reactor_impl.jpg)

上述代码描述了上图中一个handler的实现，但是其中会存在如下问题：

1. 线程过多，仅仅适合处理并发量不大的网络应用

可伸缩性的目标：

1. 当有更多的负载时可以优雅的降级

2. 持续的提升性能当CPU，内存等资源提升时

3. 满足所有的性能要求

   1. 短延时
   2. 满足峰值需求
   3. 可调节的服务质量

4. 分而治之的方法

   1. 将整个处理过程分解成一些细小的任务，每一个任务都在不阻塞的情况下
   2. 当每一个任务可用时会被CPU执行，IO事件会作为触发器
   3. 非阻塞的读和写，分发能感知IO事件的任务

### Reactor模式

==reactor==：通过分发恰当的处理器来实现IO事件的相应，类似于AWT的线程，在netty中，对应为EventWorkLoop

==handler==：处理非阻塞的动作，类似于AWT里面的ActionLinsteners

管理事件的绑定：类似于AWT里面的addActionListeners

 ![avatar](/image/reactor_arch.jpg)



上图为基础版本的reactor模型设计

### 单线程版本设计步骤如下：

1. Setup：

![avatar](/image/reactor_setup.jpg)

注册OP_ACCEPT事件，

2. Dispatch Loop

![avatar](image/reactor_dispatchloop.jpg)

线程方法，在一个新的线程里面执行。在获得了selectionkey进行分发。类似于netty中的bossgroup，在收到了新的连接之后进行分发到workgroup执行。

3. Acceptor

![avatar](/image/reactor_acceptor.jpg)

获得连接后，新建一个handler对象，进行实际的处理

4. Handler

![avatar](/image/reactor_handler.jpg)

```sk.interestOps(SelectionKey.OP_READ);```才是真正的设置监听的事件

sel.wakeup()```：指的是在selecte阻塞后立即返回，不会继续阻塞，让第一个尚未返回的选择操作返回。

5. Request Handling

![avatar](/image/reactor_re_handling.jpg)

自己的业务逻辑，```read()```和```write()```分别对应读和写方法，在```read()```方法中的```process()```执行完后，会把状态设置为```SENDING```，并添加一个监听操作```OP_WRITE```，以便进行下一轮的发送。默认为```READING```。

6. Pre-State Handlers

![avatar](/image/reactor_pre_state.jpg)

重新绑定handler作为操作对象

### 多线程的设计

用于多处理器中。将非IO得处理交给其他线程。多reactor线程，多个IO处理线程可以减轻IO压力。最好使用线程池来进行调节。

![avatar](/image/reactor_muti.jpg)

在线程池里面进行计算，把结果送回给reactor进行返回给客户端

![avatar](/image/reactor_muti_handler.jpg)

在```process()```中处理，注意，```processAndHandOff()```是同步方法。

### 多Reactor线程

使用Reactor池，匹配CPU和IO的速率。每一个Reactor都有自己的Selector，线程和分发循环。但是会有一个主Reactor去相应外部的连接，在交由线程池计算后会交给Reactor池进行返回结果。

具体理解可以类比到netty中，mainReactor------bossGroup，subReactor------workGroup

![avatar](/image/reactor_muti_reactor.jpg)

mainReactor一般只有一个，而subReactor一般会有多个。

bossGroup监听OP_ACCEPT事件，而workGroup监听OP_READ事件。

> 不要将耗时代码放在channelRead0中，否则会阻塞该方法，影响读取数据，应该放在业务线程中。

## netty的自适应缓冲区和堆外内存创建方式

### 提前分配好各个大小的缓冲区

类```AdaptiveRecvByteBufAllocator```会自动的根据返馈增加或者减小接收缓冲的大小

当上一次读取的buffer读满了后，这次会增加缓冲区的大小，反之，会保持相同的大小。默认初始值为1024，但以后不会小于64，且不会超过65536。

```java
static final int DEFAULT_MINIMUM = 64;
    static final int DEFAULT_INITIAL = 1024;
    static final int DEFAULT_MAXIMUM = 65536;

    private static final int INDEX_INCREMENT = 4;
    private static final int INDEX_DECREMENT = 1;

    private static final int[] SIZE_TABLE;

    static {
        List<Integer> sizeTable = new ArrayList<Integer>();
        //每一个元素相差16
        //16,32,48.。。。512
        for (int i = 16; i < 512; i += 16) {
            sizeTable.add(i);
        }

        //把每一个乘以2之后的结果填入数组
        //里面为512,1024,2048.。。。。65536
        for (int i = 512; i > 0; i <<= 1) {
            sizeTable.add(i);
        }

        SIZE_TABLE = new int[sizeTable.size()];
        for (int i = 0; i < SIZE_TABLE.length; i ++) {
            SIZE_TABLE[i] = sizeTable.get(i);
        }
    }
```

提前按从小到大的分配好各个不同大小的缓冲区，各个缓冲区的大小为16,32,48,64，。。。。。512，1024,2048,4096，。。。。65536.

注意到每次需要申请的缓冲区大小为```AdaptiveRecvByteBufAllocator```内部类```HandleImpl```中的```guess()```方法，并且会将本次实际写入大小给记录下来，通过```record(int actualReadBytes)```，而真正申请内存的方法在```HandleImpl```的父类```MaxMessageHandle```类中。

```java
@Override
        public ByteBuf allocate(ByteBufAllocator alloc) {
            return alloc.ioBuffer(guess());
        }
```

该方法会调用其接口的实现类```AbstractByteBufAllocator```中的```public ByteBuf ioBuffer(int initialCapacity)```方法

```java
@Override
    public ByteBuf ioBuffer(int initialCapacity) {
        if (PlatformDependent.hasUnsafe()) {
            return directBuffer(initialCapacity);
        }
        return heapBuffer(initialCapacity);
    }
```

判断是不是unsafe的方式，若是unsafe方式，则返回直接缓冲，否则返回堆内缓冲区。至于如何判断是否为unsafe方式创建缓冲区，可参考如下方法：

```java
/**
     * Return {@code true} if {@code sun.misc.Unsafe} was found on the classpath and can be used for accelerated
     * direct memory access.
     */
    public static boolean hasUnsafe() {
        return UNSAFE_UNAVAILABILITY_CAUSE == null;
    }
```

其中，UNSAFE_UNAVAILABILITY_CAUSE字段通过```PlatformDependent```中的```private static Throwable unsafeUnavailabilityCause0()```方法获取：

```java
private static Throwable unsafeUnavailabilityCause0() {
        if (isAndroid()) {
            logger.debug("sun.misc.Unsafe: unavailable (Android)");
            return new UnsupportedOperationException("sun.misc.Unsafe: unavailable (Android)");
        }
        Throwable cause = PlatformDependent0.getUnsafeUnavailabilityCause();
        if (cause != null) {
            return cause;
        }

        try {
            boolean hasUnsafe = PlatformDependent0.hasUnsafe();
            logger.debug("sun.misc.Unsafe: {}", hasUnsafe ? "available" : "unavailable");
            return hasUnsafe ? null : PlatformDependent0.getUnsafeUnavailabilityCause();
        } catch (Throwable t) {
            logger.trace("Could not determine if Unsafe is available", t);
            // Probably failed to initialize PlatformDependent0.
            return new UnsupportedOperationException("Could not determine if Unsafe is available", t);
        }
    }
```

先判断是不是Android系统，接着判断是否有系统属性，如果有，则创建堆外直接内存，否则，创建堆内内存。

### 对于堆外内存

如果是创建堆外直接内存，可以参考在```UnpooledByteBufAllocator```中的方法

```java
/**
     * Creates a new direct buffer.
     *
     * @param initialCapacity the initial capacity of the underlying direct buffer
     * @param maxCapacity     the maximum capacity of the underlying direct buffer
     */
    public UnpooledUnsafeDirectByteBuf(ByteBufAllocator alloc, int initialCapacity, int maxCapacity) {
        super(maxCapacity);
        if (alloc == null) {
            throw new NullPointerException("alloc");
        }
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("initialCapacity: " + initialCapacity);
        }
        if (maxCapacity < 0) {
            throw new IllegalArgumentException("maxCapacity: " + maxCapacity);
        }
        if (initialCapacity > maxCapacity) {
            throw new IllegalArgumentException(String.format(
                    "initialCapacity(%d) > maxCapacity(%d)", initialCapacity, maxCapacity));
        }

        this.alloc = alloc;
        setByteBuffer(allocateDirect(initialCapacity), false);
    }
```

对于上述代码的第24行，的setByteBuffer方法调用，初始化一个堆外内存，并设置好buffer中的那三个属性，接着设置初始值。

```java
final void setByteBuffer(ByteBuffer buffer, boolean tryFree) {
        if (tryFree) {
            ByteBuffer oldBuffer = this.buffer;
            if (oldBuffer != null) {
                if (doNotFree) {
                    doNotFree = false;
                } else {
                    freeDirect(oldBuffer);
                }
            }
        }
        this.buffer = buffer;
        memoryAddress = PlatformDependent.directBufferAddress(buffer);
        tmpNioBuf = null;
        capacity = buffer.remaining();
    }
```

### 对于堆内内存

```java
@Override
    protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
        return PlatformDependent.hasUnsafe() ?
                new InstrumentedUnpooledUnsafeHeapByteBuf(this, initialCapacity, maxCapacity) :
                new InstrumentedUnpooledHeapByteBuf(this, initialCapacity, maxCapacity);
    }
```

## Reactor模式的角色分析

原始的的连接方式存在的问题：一个socket对应一个线程。Reactor模式一共有5种角色构成。

![avatar](/image/reactor_paper.jpg)

#### Handle（句柄或者描述符）

本质上表示一种资源，由操作系统提供。用于表示一个个的事件，例如：文件描述符，针对网络编程中的socket描述符。事件既可以来自于外部，也可以来自于内部。外部事件例如客户端的连接请求，客户端发来的数据等；内部事件例如操作系统产生的定时器事件等。本质上就是文件描述符。Handle是事件产生的发源地

#### Synchronous Event Demultiplexer（同步事件分离器）
本身是一个系统调用，用于等待事件的发生。事件可能是一个，也可能是多个。调用方在调用它的时候会被阻塞，一直阻塞到同步事件分离器上有事件产生为止。对于Linux来说，同步事件分离器指的是常用的I/O多路复用机制，例如，selecte、poll、epoll等。==对应到Java NIO中的selector组件，对应的阻塞方法是select方法==

#### Event Handler（事件处理器）

本身由多个回调方法来构成，这些回调方法构成了与应用相关的对于某个事件的反馈机制。对应到Java NIO中为用户自己提供处理逻辑。对于netty来说，netty本身给我们提供了一系列的事件处理器，是对Java NIO的更进一层的封装。例如：```SimpleChannelInboundHandler<String>```

#### Concrete Event Handler（具体事件处理器）
 是事件处理器的实现，它本身实现了事件处理器所提供的各个回调方法，从而实现了特定于业务的逻辑。本质上就是我们自己编写的一个个处理器的实现。例如：```public class MyServerHandler extends SimpleChannelInboundHandler<String>```

#### Initiation Dispatcher（初始分发器）

实际上就是reactor角色。它本身定义了一些规范，这些规范用于控制事件的调度方式，同时又提供了应用进行事件处理器的注册，删除等设施。它本身是整个事件处理器的核心所在。会通过同步事件分离器来等待事件的发生，一旦事件发生，它会先会分理出每一个事件（取出每一个SelectionKey），然后调用事件处理器，最后调用相关的回调方法来处理这些事件。

###  Reactor与netty组件的对比

#### Reactor模式的流程

1. 当应用向Initiation Dispatcher注册具体的事件处理器时，应用会标识出该事件处理器希望Initiation Dispatcher在某个事件发生时向其通知的该事件，该事件与Handle关联
2. Initiation Dispatcher会要求每个事件处理器向其传递内部的Handle，该Handle向操作系统标识了事件处理器
3. 当所有的事件处理器注册完毕后，应用会调用handle_events方法来启动Initiation Dispatcher的事件循环，这时，Initiation Dispatcher会将每个注册的事件管理器的Handle合并起来，并使用同步事件分离器来等待这些事件的发生。例如，TCP协议层会使用select同步事件分离器来等待客户端发送的数据到达连接的socket handle上
4. 当与某个事件源对于的Handle变为ready状态时，例如TCP socket变为等待读状态时，同步事件分离器会通知Initiation Dispatcher
5. Initiation Dispatcher会触发事件处理器的回调方法，从而响应这个处于ready状态的Handle。当事件发生时，Initiation Dispatcher会将被事件源激活的Handle作为“key”来寻找并分发恰当的事件处理器回调方法
6. Initiation Dispatcher会回调事件处理器的handle_events回调方法来哦执行特定于应用的功能（开发者自己编写的业务代码），从而响应那个事件，所发生的事件类型可以作为该方法参数内部使用来执行额外的特定于服务的分离与分发

## Channel与ChannelPipeline的关联关系与应用

### Channel

```public interface Channel extends AttributeMap, ChannelOutboundInvoker, Comparable<Channel>```

为一个网络socket的连接点，或者为一个可以进行IO操作的组件。能够提供下面这些功能：

1. 当前channel的状态，是否打开等
2. 可以配置channel的参数
3. 提供当前channel支持的IO操作，读写等
4. 提供了channelpipeline，可以处理当前channel相关的所有请求以及IO事件

所有的IO操作都是异步的。反之，会返回一个ChannelFuture实例，通过这个来获得操作的结果。且每一个Channel都有一个其唯一的id，来分辨它，```public interface ChannelId extends Serializable, Comparable<ChannelId>```

一个Channel时可能存在一个父亲节点的，例如，SocketChannel是作为ServerSocketChannel接收方法调用时返回的，那么Channel调用parent()方法时会返回ServerSocketChannel实例

> 在使用完Channel之后必须调用close()方法来关闭资源

在创建Channel的同时会创建好ChannelPipeline

### ChannelPipeline

初始化地点：在初始化channel的时候在```ChannelPipeline p = channel.pipeline();```这一行的pipeline()方法中被初始化，

```public abstract class AbstractChannel extends DefaultAttributeMap implements Channel```注意到该类为Channel接口的一个实现，该类中有一个成员变量就是```private final DefaultChannelPipeline pipeline```，在该类的构造方法中，pipeline对象被初始化

```java
/**
     * Creates a new instance.
     *
     * @param parent
     *        the parent of this channel. {@code null} if there's no parent.
     */
    protected AbstractChannel(Channel parent) {
        this.parent = parent;
        id = newId();
        unsafe = newUnsafe();
        pipeline = newChannelPipeline();
    }
```

pipeline的创建过程：维护两个变量，head和tail，类似于一个链表，用于记录以后要加入的handler

```java
protected DefaultChannelPipeline(Channel channel) {
        this.channel = ObjectUtil.checkNotNull(channel, "channel");
        succeededFuture = new SucceededChannelFuture(channel, null);
        voidPromise =  new VoidChannelPromise(channel, true);

        tail = new TailContext(this);
        head = new HeadContext(this);

        head.next = tail;
        tail.prev = head;
    }
```

其中，head和tail均为```AbstractChannelHandlerContext```类型。他是ChannelHandler的列表，会处理并拦截入站和出站的事件。用于处理如何控制这些事件，而且各个ChannelHandlers是如何交互的。客户端的请求会先经过过滤器然后再被真正的响应。每一个channel都有一个自己的pipeline，且pipeline在channel创建的同时会被创建。一个handler处理完之后会被发送给理他最近的handler，通过ChannelHandlerContext传播

```java
*                                                 I/O Request
*                                            via {@link Channel} or
*                                        {@link ChannelHandlerContext}
*                                                      |
*  +---------------------------------------------------+---------------+
*  |                           ChannelPipeline         |               |
*  |                                                  \|/              |
*  |    +---------------------+            +-----------+----------+    |
*  |    | Inbound Handler  N  |            | Outbound Handler  1  |    |
*  |    +----------+----------+            +-----------+----------+    |
*  |              /|\                                  |               |
*  |               |                                  \|/              |
*  |    +----------+----------+            +-----------+----------+    |
*  |    | Inbound Handler N-1 |            | Outbound Handler  2  |    |
*  |    +----------+----------+            +-----------+----------+    |
*  |              /|\                                  .               |
*  |               .                                   .               |
*  | ChannelHandlerContext.fireIN_EVT() ChannelHandlerContext.OUT_EVT()|
*  |        [ method call]                       [method call]         |
*  |               .                                   .               |
*  |               .                                  \|/              |
*  |    +----------+----------+            +-----------+----------+    |
*  |    | Inbound Handler  2  |            | Outbound Handler M-1 |    |
*  |    +----------+----------+            +-----------+----------+    |
*  |              /|\                                  |               |
*  |               |                                  \|/              |
*  |    +----------+----------+            +-----------+----------+    |
*  |    | Inbound Handler  1  |            | Outbound Handler  M  |    |
*  |    +----------+----------+            +-----------+----------+    |
*  |              /|\                                  |               |
*  +---------------+-----------------------------------+---------------+
*                  |                                  \|/
*  +---------------+-----------------------------------+---------------+
*  |               |                                   |               |
*  |       [ Socket.read() ]                    [ Socket.write() ]     |
*  |                                                                   |
*  |  Netty Internal I/O Threads (Transport Implementation)            |
*  +-------------------------------------------------------------------+
```

入站和出站操作互不影响。对于入站事件，如果读取的数据超出了入站处理器能够接纳的范围，超出的部分会被丢弃。假设有以下代码设置处理器：

```java
ChannelPipeline} p = ...;
 * p.addLast("1", new InboundHandlerA());
 * p.addLast("2", new InboundHandlerB());
 * p.addLast("3", new OutboundHandlerA());
 * p.addLast("4", new OutboundHandlerB());
 * p.addLast("5", new InboundOutboundHandlerX());
```

那么处理顺序为：

1. 入站方向：1-2-5，因为3,4不是入站处理器
2. 出站方向：5-4-3，因为1,2不是出站处理器

一个处理器需要调用位于```ChannelHandlerContext```中的事件传播方法来进行事件传播，将事件传递给下一个处理器。这些方法包括：

1. 入站事件传播方法：
   1. ```ChannelHandlerContext#fireChannelRegistered()```
   2. ```ChannelHandlerContext#fireChannelActive()```
   3. ```ChannelHandlerContext#fireChannelRead(Object)```
   4. ```ChannelHandlerContext#fireChannelReadComplete()```
   5. ```ChannelHandlerContext#fireExceptionCaught(Throwable)```
   6. ```ChannelHandlerContext#fireUserEventTriggered(Object)```
   7. ```ChannelHandlerContext#fireChannelWritabilityChanged()```
   8. ```ChannelHandlerContext#fireChannelInactive()```
   9. ```ChannelHandlerContext#fireChannelUnregistered()```
2. 出站事件传播方法：
   1. ```ChannelHandlerContext#bind(SocketAddress, ChannelPromise)```
   2. ```ChannelHandlerContext#connect(SocketAddress, SocketAddress, ChannelPromise)```
   3. ```ChannelHandlerContext#write(Object, ChannelPromise)```
   4. ```ChannelHandlerContext#flush()```
   5. ```ChannelHandlerContext#read()```
   6. ```ChannelHandlerContext#disconnect(ChannelPromise)```
   7. ```ChannelHandlerContext#close(ChannelPromise)```
   8. ```ChannelHandlerContext#deregister(ChannelPromise)```

事件传播的例子：

```java
* public class MyInboundHandler extends {@link ChannelInboundHandlerAdapter} {
 *     {@code @Override}
 *     public void channelActive({@link ChannelHandlerContext} ctx) {
 *         System.out.println("Connected!");
 *         ctx.fireChannelActive();
 *     }
 * }
```

普通的方式会阻塞IO线程，netty推荐用以下方式进行开发：将自己的handler放置于一个线程组中执行。

```java
pipeline.addLast(group, "handler", new MyBusinessLogicHandler());
```

## netty常量池实现以及ChannelOptions与Attribute作用分析

### ChannelOptions---配置和TCP相关的设置

可以以安全的方式进行配置ChannelConfig

```public class ChannelOption<T> extends AbstractConstant<ChannelOption<T>>```

其内部维护了一个ConstantPool，里面存放了各种参数。也可以自己去显式的指明，```.option()```方法

```java
private static final ConstantPool<ChannelOption<Object>> pool = new ConstantPool<ChannelOption<Object>>() {
        @Override
        protected ChannelOption<Object> newConstant(int id, String name) {
            return new ChannelOption<Object>(id, name);
        }
    };
```

而ConstantPool的定义为：

```public abstract class ConstantPool<T extends Constant<T>>```

他有一个内部方法，```private T getOrCreate(String name)```，如果没有，则创建，如果该option已经存在，则返回该option，根据名字获取指定的常量

```java
/**
     * Get existing constant by name or creates new one if not exists. Threadsafe
     *
     * @param name the name of the {@link Constant}
     */
    private T getOrCreate(String name) {
        T constant = constants.get(name);
        if (constant == null) {
            final T tempConstant = newConstant(nextId(), name);
            
            //如果没有这个key则插入
            constant = constants.putIfAbsent(name, tempConstant);
            if (constant == null) {
                return tempConstant;
            }
        }

        return constant;
    }
```

注意到第13~15行的double-check，因为这个方法会被并发地调用，如果不是double-check的话会造成上一个线程刚刚修改，还没执行到第12行，一下个线程已经进入方法的第13行，此时这个检查会发现constant为null，就会返回tempConstant。设置option的操作如下：```setChannelOptions(channel, options, logger);```

会调用以下代码：

```java
@SuppressWarnings("unchecked")
    private static void setChannelOption(
            Channel channel, ChannelOption<?> option, Object value, InternalLogger logger) {
        try {
            if (!channel.config().setOption((ChannelOption<Object>) option, value)) {
                logger.warn("Unknown channel option '{}' for channel '{}'", option, channel);
            }
        } catch (Throwable t) {
            logger.warn(
                    "Failed to set channel option '{}' with value '{}' for channel '{}'", option, value, channel, t);
        }
    }
```

注意到上述代码的第5行，真正调用了ChannelConfig接口中的setOption方法，并使用的是DefaultChannelConfig的实现

```java
@Override
    @SuppressWarnings("deprecation")
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        validate(option, value);

        if (option == CONNECT_TIMEOUT_MILLIS) {
            setConnectTimeoutMillis((Integer) value);
        } else if (option == MAX_MESSAGES_PER_READ) {
            setMaxMessagesPerRead((Integer) value);
        } else if (option == WRITE_SPIN_COUNT) {
            setWriteSpinCount((Integer) value);
        } else if (option == ALLOCATOR) {
            setAllocator((ByteBufAllocator) value);
        } else if (option == RCVBUF_ALLOCATOR) {
            setRecvByteBufAllocator((RecvByteBufAllocator) value);
        } else if (option == AUTO_READ) {
            setAutoRead((Boolean) value);
        } else if (option == AUTO_CLOSE) {
            setAutoClose((Boolean) value);
        } else if (option == WRITE_BUFFER_HIGH_WATER_MARK) {
            setWriteBufferHighWaterMark((Integer) value);
        } else if (option == WRITE_BUFFER_LOW_WATER_MARK) {
            setWriteBufferLowWaterMark((Integer) value);
        } else if (option == WRITE_BUFFER_WATER_MARK) {
            setWriteBufferWaterMark((WriteBufferWaterMark) value);
        } else if (option == MESSAGE_SIZE_ESTIMATOR) {
            setMessageSizeEstimator((MessageSizeEstimator) value);
        } else if (option == SINGLE_EVENTEXECUTOR_PER_GROUP) {
            setPinEventExecutorPerGroup((Boolean) value);
        } else {
            return false;
        }

        return true;
    }
```

### AttributeKey

也是继承自AbstractConstant，AtttibuteKey和Attribute为一对键值对，存储于AttributeMap中，作用。可以在一个流程中进行数据的共享，前一个流程处理完的数据可以被下一个流程使用。

```java
/**
 * Holds {@link Attribute}s which can be accessed via {@link AttributeKey}.
 *
 * Implementations must be Thread-safe.
 */
public interface AttributeMap {
    /**
     * Get the {@link Attribute} for the given {@link AttributeKey}. This method will never return null, but may return
     * an {@link Attribute} which does not have a value set yet.
     */
    <T> Attribute<T> attr(AttributeKey<T> key);

    /**
     * Returns {@code} true if and only if the given {@link Attribute} exists in this {@link AttributeMap}.
     */
    <T> boolean hasAttr(AttributeKey<T> key);
}
```

## Channel与ChannelHandler和ChannelHandlerContext之间的关系

### ChannelHandler与ChannelHandlerContext的关系

我们在服务端代码里面编写的MyServerInitializer是继承自ChannelInitializer，而ChannelInitializer是继承即ChannelInboundHandlerAdapter，在里面有一个```addLast()```方法，接收可变长度的ChannelHandler对象。

```java
@Override
    public final ChannelPipeline addLast(EventExecutorGroup executor, ChannelHandler... handlers) {
        if (handlers == null) {
            throw new NullPointerException("handlers");
        }

        for (ChannelHandler h: handlers) {
            if (h == null) {
                break;
            }
            addLast(executor, null, h);
        }

        return this;
    }
```

上述在判断完handler是否为空之后，会调用真正的添加方法：

```java
@Override
    public final ChannelPipeline addLast(EventExecutorGroup group, String name, ChannelHandler handler) {
        //AbstractChannelHandlerContext实现了ChannelHandlerContext
        final AbstractChannelHandlerContext newCtx;
        synchronized (this) {
            
            //检查该handler是否已经被添加进去
            checkMultiplicity(handler);

            //在这里会直接创建出一个DefaultChannelHandlerContext对象
            newCtx = newContext(group, filterName(name, handler), handler);

            addLast0(newCtx);

            // If the registered is false it means that the channel was not registered on an eventloop yet.
            // In this case we add the context to the pipeline and add a task that will call
            // ChannelHandler.handlerAdded(...) once the channel is registered.
            //一旦这个变量registered被设置为true，那么就不会再被改变了
            //如果为false，说明这个channel没有被注册到事件循环中
            //并不是立即被移除，而是会在一系列任务之后被移除
            if (!registered) {
                newCtx.setAddPending();
                callHandlerCallbackLater(newCtx, true);
                return this;
            }

            EventExecutor executor = newCtx.executor();
            if (!executor.inEventLoop()) {
                newCtx.setAddPending();
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        callHandlerAdded0(newCtx);
                    }
                });
                return this;
            }
        }
        callHandlerAdded0(newCtx);
        return this;
    }
```

第9行为内部变量赋值方法，将新创建的handler放在最后一个handler前面

```java
private void addLast0(AbstractChannelHandlerContext newCtx) {
        AbstractChannelHandlerContext prev = tail.prev;
        newCtx.prev = prev;
        newCtx.next = tail;
        prev.next = newCtx;
        tail.prev = newCtx;
    }
```

ChannelHandlerContext既可以通知和调用最近的ChannelPipeline，也可以修改其所属的ChannelPipeline

可以提前获取一个ChannelHandlerContext，然后在另一个时间或者另一个线程中调用它

```java
public class MyHandler extends {@link ChannelDuplexHandler} {
 *
 *     <b>private {@link ChannelHandlerContext} ctx;</b>
 *
 *     public void beforeAdd({@link ChannelHandlerContext} ctx) {
 *         <b>this.ctx = ctx;</b>
 *     }
 *
 *     public void login(String username, password) {
 *         ctx.write(new LoginMessage(username, password));
 *     }
 *     ...
 * }
```

**一个handler可以拥有多个ChannelHandlerContext对象**

ChannelHandlerContext对象既可以获取到channel对象，也可以获取到和channel对象相关联的handler对象，甚至可以获取到channelpipeline对象。类似于channel和channelhandler的桥梁。

```java
final class DefaultChannelHandlerContext extends AbstractChannelHandlerContext {

    private final ChannelHandler handler;

    DefaultChannelHandlerContext(
            DefaultChannelPipeline pipeline, EventExecutor executor, String name, ChannelHandler handler) {
        super(pipeline, executor, name, isInbound(handler), isOutbound(handler));
        if (handler == null) {
            throw new NullPointerException("handler");
        }
        this.handler = handler;
    }

    @Override
    public ChannelHandler handler() {
        return handler;
    }

    //是否为入站方法
    private static boolean isInbound(ChannelHandler handler) {
        return handler instanceof ChannelInboundHandler;
    }

    //是否为出站方法
    private static boolean isOutbound(ChannelHandler handler) {
        return handler instanceof ChannelOutboundHandler;
    }
}
```

实际上，第3行的本地变量handler就是context对象维护的那个handler，可供channel使用的handler

在完成了handler的添加之后，会调用```private void callHandlerAdded0(final AbstractChannelHandlerContext ctx) ```方法里面的```ctx.handler().handlerAdded(ctx);```就调用了那个前面的handler添加的事件代码。

### ChannelInitializer的移除

当```protected abstract void initChannel(C ch) throws Exception;```调用时，方法返回后会把这个实例会从channel的channelpipeline中被移除。因为它自己不是一个handler，它仅仅起到了一个初始化的作用。当handler被调用后，会调用以下方法，因此这个实例就被移除了。

```java
/**
     * {@inheritDoc} If override this method ensure you call super!
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isRegistered()) {
            // This should always be true with our current DefaultChannelPipeline implementation.
            // The good thing about calling initChannel(...) in handlerAdded(...) is that there will be no ordering
            // surprises if a ChannelInitializer will add another ChannelInitializer. This is as all handlers
            // will be added in the expected order.
            initChannel(ctx);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean initChannel(ChannelHandlerContext ctx) throws Exception {
        if (initMap.putIfAbsent(ctx, Boolean.TRUE) == null) { // Guard against re-entrance.
            try {
                initChannel((C) ctx.channel());
            } catch (Throwable cause) {
                // Explicitly call exceptionCaught(...) as we removed the handler before calling initChannel(...).
                // We do so to prevent multiple calls to initChannel(...).
                exceptionCaught(ctx, cause);
            } finally {
                remove(ctx);
            }
            return true;
        }
        return false;
    }

    private void remove(ChannelHandlerContext ctx) {
        try {
            ChannelPipeline pipeline = ctx.pipeline();
            if (pipeline.context(this) != null) {
                pipeline.remove(this);
            }
        } finally {
            initMap.remove(ctx);
        }
    }
```

## Channel注册流程解析

详见在AbstractBootstrap类中的```final ChannelFuture initAndRegister()```方法内的```ChannelFuture regFuture = config().group().register(channel);```方法，为对channel的注册

### ```config()```方法

为本类的抽象方法，用于获取该channel的config。```public abstract AbstractBootstrapConfig<B, C> config();```在服务端，实现由ServerBootstrap完成，返回一个ServerBootstrapConfig对象

```java
 @Override
    public final ServerBootstrapConfig config() {
        return config;
    }

private final ServerBootstrapConfig config = new ServerBootstrapConfig(this);
```

### ```group()```方法

在上述```config()```方法返回后，调用得到的ServerBootstrapConfig对象中的```group()```方法

```java
/**
     * Returns the configured {@link EventLoopGroup} or {@code null} if non is configured yet.
     */
    @SuppressWarnings("deprecation")
    public final EventLoopGroup group() {
        return bootstrap.group();
    }

```

实际上又会调用到AbstractBootstrap类中的group方法，并返回EventLoopGroup类型，返回的group实际为类中的一个属性。在构造方法里面被传递进来的AbstractBootstrap中的group初始化。在本例中，实际上就是NioEventLoopGroup类型。

```java
/**
     * Returns the configured {@link EventLoopGroup} or {@code null} if non is configured yet.
     *
     * @deprecated Use {@link #config()} instead.
     */
    @Deprecated
    public final EventLoopGroup group() {
        return group;
    }

volatile EventLoopGroup group;

AbstractBootstrap(AbstractBootstrap<B, C> bootstrap) {
        group = bootstrap.group;
        channelFactory = bootstrap.channelFactory;
        handler = bootstrap.handler;
        localAddress = bootstrap.localAddress;
        synchronized (bootstrap.options) {
            options.putAll(bootstrap.options);
        }
        synchronized (bootstrap.attrs) {
            attrs.putAll(bootstrap.attrs);
        }
    }
```

### ```register(channel)```方法

在上述代码返回后，会调用NioEventLoopGroup类的方法中。但是实际上会调用MultithreadEventLoopGroup类中的```public ChannelFuture register(Channel channel)```方法。而NioEventLoopGroup是MultithreadEventLoopGroup的一个子类，所以最后还是调用到了父类中的方法。

## Channel选择器工厂与轮训算法的实现

### Chooser的实现

在```register(channel)```方法被调用时会调用MultithreadEventLoopGroup类中的```next()```方法，这个next()方法是位于MultithreadEventExecutorGroup类中。

```java
@Override
    public EventExecutor next() {
        return chooser.next();
    }
```

返回一个EventExecutor对象，这个chooser在它的构造方法中创造。

```java
chooser = chooserFactory.newChooser(children);
```

这个neChooser方法在DefaultEventExecutorChooserFactory类中实现，会根据执行器的数量进行区分创建的chooser的种类。

```java
@SuppressWarnings("unchecked")
    @Override
    public EventExecutorChooser newChooser(EventExecutor[] executors) {
        if (isPowerOfTwo(executors.length)) {
            return new PowerOfTwoEventExecutorChooser(executors);
        } else {
            return new GenericEventExecutorChooser(executors);
        }
    }
```

上面两个chooser实际上就是一个轮询的实现：都是round-robin算法

```java
private static final class PowerOfTwoEventExecutorChooser implements EventExecutorChooser {
        private final AtomicInteger idx = new AtomicInteger();
        private final EventExecutor[] executors;

        PowerOfTwoEventExecutorChooser(EventExecutor[] executors) {
            this.executors = executors;
        }

        @Override
        public EventExecutor next() {
            return executors[idx.getAndIncrement() & executors.length - 1];
        }
    }

    private static final class GenericEventExecutorChooser implements EventExecutorChooser {
        private final AtomicInteger idx = new AtomicInteger();
        private final EventExecutor[] executors;

        GenericEventExecutorChooser(EventExecutor[] executors) {
            this.executors = executors;
        }

        @Override
        public EventExecutor next() {
            return executors[Math.abs(idx.getAndIncrement() % executors.length)];
        }
    }
```

### Register的实现

在上述register()方法后，会调用next()方法获取一个EventLoop对象，然后再调用该对象的register方法

```java
@Override
    public ChannelFuture register(Channel channel) {
        return next().register(channel);
    }

//在SingleThreadEventLoop中实现
@Override
    public ChannelFuture register(Channel channel) {
        return register(new DefaultChannelPromise(channel, this));
    }
```

这个register实际上会进入到SingleThreadEventLoop类中的方法。该类是一个EventLoop的父类，它将所有提交到这里的任务都放置于一个线程中执行。线程体现在它的父类AbstractScheduledEventExecutor的一个成员变量：```private volatile Thread thread;```

上述方法中的构造方法的调用就是将channel对象赋值给ChannelPromise对象。构造方法会新创建一个ChannelPromise对象，接着会调用register方法，

```java
@Override
    public ChannelFuture register(final ChannelPromise promise) {
        ObjectUtil.checkNotNull(promise, "promise");
        promise.channel().unsafe().register(this, promise);
        return promise;
    }
```

首先第4行代码点调用获取到promise中的channel对象实例。接着调用channel对象的unsafe方法，该方法返回一个内部的对象，用于执行一些不安全的操作。而unsafe为一个channel接口内部的接口，实际上调用的是AbstractNioChannel类中的实现：

```java
@Override
    public NioUnsafe unsafe() {
        return (NioUnsafe) super.unsafe();
    }
```

直接返回一个Unsafe unsafe成员变量，并在构造函数初始化，这个unsafe对象的实际类型是AbstractNioMessageChannel内部类NioMessageUnsafe的实例。

```java
/**
     * Creates a new instance.
     *
     * @param parent
     *        the parent of this channel. {@code null} if there's no parent.
     */
    protected AbstractChannel(Channel parent) {
        this.parent = parent;
        id = newId();
        unsafe = newUnsafe();
        pipeline = newChannelPipeline();
    }



```

在unsafe方法返回后会继续往下调用register方法，该方法位于AbstractChannel类中。

```java
@Override
        public final void register(EventLoop eventLoop, final ChannelPromise promise) {
            if (eventLoop == null) {
                throw new NullPointerException("eventLoop");
            }
            if (isRegistered()) {
                promise.setFailure(new IllegalStateException("registered to an event loop already"));
                return;
            }
            if (!isCompatible(eventLoop)) {
                promise.setFailure(
                        new IllegalStateException("incompatible event loop type: " + eventLoop.getClass().getName()));
                return;
            }

            AbstractChannel.this.eventLoop = eventLoop;

            if (eventLoop.inEventLoop()) {
                register0(promise);
            } else {
                try {
                    eventLoop.execute(new Runnable() {
                        @Override
                        public void run() {
                            register0(promise);
                        }
                    });
                } catch (Throwable t) {
                    logger.warn(
                            "Force-closing a channel whose registration task was not accepted by an event loop: {}",
                            AbstractChannel.this, t);
                    closeForcibly();
                    closeFuture.setClosed();
                    safeSetFailure(promise, t);
                }
            }
        }
```

第3~15行为判断是否已经注册等逻辑

第18行的判断为该eventLoop是否位于本线程中，其实就是调用判断执行该方法的线程是不是就是AbstractScheduledEventExecutor内部维护的那个线程。

### 需要判断并可能向线程池提交任务的原因

1. 一个EventLoopGroup当中会包含一个或多个EventLoop
2. 一个EventLoop在它的整个生命周期中都只会与唯一一个Thread进行绑定
3. 所有由EventLoop所处理的各种I/O事件都将在它所关联的Thread上进行处理
4. 一个channel在它的整个生命周期中只会注册在一个EventLoop上
5. 一个EventLoop在运行过程中，会被分配一个或者多个channel

如果if判断返回了true，说明正在执行该方法的线程就是EventLoop当中的线程，那么可以直接交由它来完成注册，但是，如果if返回了false，那么说明其他线程正在执行该方法，所以需要向EventLoop提交注册任务。保证注册方法是由EventLoop中的线程对象完成。==因此，在netty中，channel的实现是线程安全的，基于此，我们可以存储一个channel的引用，并且在需要向远程端点发送数据时，通过这个引用来调用channel相应的方法，即便当时有很多的线程在使用他也不会出现多线程问题，而且，消息一定会按照一定的顺序发送出去==

> 在业务开发中，不要将长时间执行的耗时任务放入到EventLoop的执行队列中，而是需要一个专门的EventExecutor

通常会有两种解决方案：

1. 在ChannelHnamdler的回调方法中，使用自己定义的业务线程池，这样就可以实现异步调用
2. 借助于netty提供的向ChannelPipeline添加ChannelHandler时调用的addLast方法来传递EventExecutor，因为addLast中的第一个参数就是一个EventExecutorGroup，会把提交的handler放于一个线程组中，不同于IO线程。

若执行方法的线程就是类中维护的工作线程，那么会调用19行的代码，register0方法又会调用doRegister()方法，它的实现位于AbstractNioChannel中

```java
@Override
    protected void doRegister() throws Exception {
        boolean selected = false;
        for (;;) {
            try {
                selectionKey = javaChannel().register(eventLoop().unwrappedSelector(), 0, this);
                return;
            } catch (CancelledKeyException e) {
                if (!selected) {
                    // Force the Selector to select now as the "canceled" SelectionKey may still be
                    // cached and not removed because no Select.select(..) operation was called yet.
                    eventLoop().selectNow();
                    selected = true;
                } else {
                    // We forced a select operation on the selector before but the SelectionKey is still cached
                    // for whatever reason. JDK bug ?
                    throw e;
                }
            }
        }
    }
```

其中有一个死循环，不断地去检测，注册是否成功，回到了Java NIO中的向selector中注册channel。

## netty异步读写和观察者模式的实现

通过使用Future对象来完成异步读写操作。通过一个监听器ChannelFutureListener可以监视事件执行的结果。会把listener存放于一个集合之中，当有事件发生时会由主题对象遍历这个集合，并调用listener中的方法。

JDK所提供的的Future只能通过手工方式检查执行结果，而这个操作会阻塞，netty对ChannelFuture进行了增强，通过ChannelFutureListener以回调的方式来获取执行结果，祛除了手工检查阻塞操作，值得注意的是：ChannelFutureListener的operationComplete方法是由IO线程执行的，因此要注意的是不要在这里执行耗时操作，否则需要通过另外的线程或线程池来执行。

### Future如何知道操作完成

例如，当写操作需要完成时，会产生如下的调用。

![avatar](/image/future_code.jpg)

在操作时，会把ChannelPromise传递进去，那么就能调用ChannelPromise中的回调方法。

### 适配器模式

提供一个适配器类，实现了接口里面的所有方法，有些可能只是一些默认或者空实现，然后自己的类继承这个适配器类，仅仅对自己感兴趣的方法进行重写，而不用重写原先接口的所有方法。

### 对象的释放

```java
@Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        boolean release = true;
        try {
            if (acceptInboundMessage(msg)) {
                @SuppressWarnings("unchecked")
                I imsg = (I) msg;
                channelRead0(ctx, imsg);
            } else {
                release = false;
                ctx.fireChannelRead(msg);
            }
        } finally {
            if (autoRelease && release) {
                ReferenceCountUtil.release(msg);
            }
        }
    }
```

第15行就是减少1次消息体msg的引用，如果为0，则会被回收，反之，retain方法会增加一次引用计数，用于维持引用的存在。这些对引用的操作都被封装在```ReferenceCounted```接口中。

在netty中有两种发送消息的方式，可以直接写到channel中，也可以写到与ChannelHandler所关联的那个ChannelHandlerContext中，对于前一种方法来说，消息会从ChannelPipeline的末尾开始流动，对于后一种方式，消息将从ChannelPipeline的下一个ChannelHandler开始流动。

![avatar](/image/channelpipeline.jpg)

1. ChannelHandlerContext与ChannelHandler之间的关联绑定关系是永远不会发生改变的，对其缓存是没有问题的
2. 对于Channel的同名方法来说，ChannelHandlerContext方法将会产生更短的事件流，所以我们应该在可能的情况下利用这个特性来提升应用性能。

## netty数据容器ByteBuf

### ByteBuf定义

```public abstract class ByteBuf implements ReferenceCounted, Comparable<ByteBuf>```

ByteBuf的创建通过```ByteBuf buf = Unpooled.buffer(10);```，使用Unpooled类中的一个buffer静态方法来创建，可以在创建之时指定初始化的容量。

可以通过循环的方式进行访问ByteBuf里面的内容

```java
for (int i = 0; i < 10; i++) {
            buf.writeByte(i);
        }

        for (int i = 0; i < buf.capacity(); i++) {
            System.out.println(buf.getByte(i));
        }
```

有两个指针来用于读访问和写访问，readerIndex，writerIndex

```java
+      +-------------------+------------------+------------------+
*      | discardable bytes |  readable bytes  |  writable bytes  |
*      |                   |     (CONTENT)    |                  |
*      +-------------------+------------------+------------------+
*      |                   |                  |                  |
*      0      <=      readerIndex   <=   writerIndex    <=    capacity
```

- 写字节时，writerIndex会不断往右边移，此时readerIndex不会动，不会超过capacity
- 读字节时，readerIndex会不断右移，直到读取到writerIndex的位置，writerIndex不会动

因此不需要```flip()```方法

在调用```discardReadBytes()```方法之前，ByteBuf里面的内容布局如下所示，可见在0~readerIndex区域内的内容均已经被读取过，可以看做是废弃的部分

```java
       +-------------------+------------------+------------------+
*      | discardable bytes |  readable bytes  |  writable bytes  |
*      +-------------------+------------------+------------------+
*      |                   |                  |                  |
*      0      <=      readerIndex   <=   writerIndex    <=    capacity
```

在调用```discardReadBytes()```方法之后，ByteBuf内的内容布局如下所示，可见，将所有的内容全部左移，并占满了之前读完了的部分。

```java
       +------------------+--------------------------------------+
*      |  readable bytes  |    writable bytes (got more space)   |
*      +------------------+--------------------------------------+
*      |                  |                                      |
* readerIndex (0) <= writerIndex (decreased)        <=        capacity
```

调用clear()方法后，会把readerIndex和writerIndex指针清零，但是不会清除里面的内容，再写的时候是覆盖之前内的内容。

可以通过调用hasArray()方法判断是不是一个堆内内存，即有一个Java本地数据维护内部数据。并通过array方法得到这个数组。

### ByteBuf实现

除了上述的Unpooled.alloc方法创建，也可以通过Unpooled.copiedBuffer通过已有的ByteBuf或者字符串中创建。```ByteBuf byteBuf = Unpooled.copiedBuffer("hello world", Charset.forName("UTF-8"));```

可以通过array方法获取内部的字节数组：

```java
public class ByteBufTest1 {
    public static void main(String[] args) {
        ByteBuf byteBuf = Unpooled.copiedBuffer("hello world", Charset.forName("UTF-8"));
        if (byteBuf.hasArray()){
            byte[] content = byteBuf.array();
            System.out.println(new String(content, Charset.forName("UTF-8")));
        }
    }
}
```

#### arrayOffset方法

字节量的偏移

#### readerIndex方法

读索引

#### writerIndex方法

写索引

#### readableBytes方法

写索引减去读索引，可读的字节数。

```java
System.out.println(new String(content, Charset.forName("UTF-8")));
System.out.println(byteBuf.arrayOffset());
System.out.println(byteBuf.readerIndex());
System.out.println(byteBuf.writerIndex());
System.out.println(byteBuf.readableBytes());
```

### netty ByteBuf所提供的三种缓冲区类型

#### heap buffer堆中缓冲

最常用的类型，ByteBuf将数据存储到JVM的堆空间中，并且将实际的数据存放到byte array中来实现

==优点==：由于数据是存储在JVM的堆中，因此可以快速的创建与快速的释放，并且它提供了直接访问内部字节数组的方法

==缺点==：每次读写数据时都需要将数据复制到直接缓冲区中再进行网络传输，还存在用户态和内核态的切换，存在上下文切换的时间。

#### direct buffer堆外缓冲

在堆之外直接分配空间，直接缓冲区并不会占用堆的空间，由操作系统在本地内存进行的数据分配

==优点==：在使用socket进行数据传递时，性能非常好，因为数据直接位于操作系统的本地内存中，所以不需要从JVM将数据复制到直接缓冲区中，性能很好

==缺点==：因为直接在操作系统中分配内存，所以内存空间的分配与释放要比堆空间更加复杂，而且速度更慢一些。==netty通过内存池来解决这个问题==，直接缓冲区不支持通过字节数组的方式来访问数据。

> 对于后端业务消息的编解码来说，推荐使用heap buffer；对于IO通信线程在读写缓冲区时，推荐使用direct buffer

#### composite buffe复合缓冲区

将多个缓冲区合并为一个缓冲区展示给用户。可以通过addComponents方法增加任意一种缓冲区。

### JDK的ByteBuffer与netty的ByteBuf的比较

1. netty的ByteBuf采用了读写索引分离的策略，一个初始化的ByteBuf的读写索引都为0
2. 当读索引与写索引处于同一个位置时，如果我们继续读取，会出现IndexOutOfBoundsException
3. 对于ByteBuf的任何读写操作都会去分别维护读索引和写索引，maxCapacity最大容量默认的限制就是Integer.MAX_VALUE

### JDK的ByteBuffer的缺点

1. final byte[] hb; 这是JDK的ByteBuffer对象中用于存储数据的对象声明；可以看到，其字节数据是被声明为final的，也就是长度是固定不变的，一旦分配好就不能动态的扩容与收缩；当待存储的数据字节量很大时，会出现异常。若要防止这种异常，那么在存储之前就需要完全直到所需存储字节的大小。如果空间不足，解决办法只有创建全新的ByteBuffer对象，再将之前的ByteBuffer中的数据复制过去，这一切操作都需要开发者自己实现
2. ByteBuffer只使用一个position指针来标志位置信息，在进行读写切换时需要调用flip()方法

### nettyByteBuf的优点

1. 存储字节的数组是动态的，默认为Integer.MAX_VALUE，动态性体现在write方法，在执行时会判断容量，不足则自动扩容
2. ByteBuf的读写索引是完全分离的。

## netty的引用计数实现与自旋锁的实现与应用

引用计数接口：```public interface ReferenceCounted```，初始化引用计数为1，retain方法会+1，release方法会-1，具体实现在AbstractReferenceCountedByteBuf类中

### retain操作

```java
@Override
    public ByteBuf retain() {
        return retain0(1);
    }

    @Override
    public ByteBuf retain(int increment) {
        return retain0(checkPositive(increment, "increment"));
    }

    private ByteBuf retain0(final int increment) {
        // all changes to the raw count are 2x the "real" change
        int adjustedIncrement = increment << 1; // overflow OK here
        int oldRef = refCntUpdater.getAndAdd(this, adjustedIncrement);
        if ((oldRef & 1) != 0) {
            throw new IllegalReferenceCountException(0, increment);
        }
        // don't pass 0!
        if ((oldRef <= 0 && oldRef + adjustedIncrement >= 0)
                || (oldRef >= 0 && oldRef + adjustedIncrement < oldRef)) {
            // overflow case
            refCntUpdater.getAndAdd(this, -adjustedIncrement);
            throw new IllegalReferenceCountException(realRefCnt(oldRef), increment);
        }
        return this;
    }
```

保证这个+1操作是原子的。对于第19行，必须要和0进行比较，如果和0一样，则不能增加，这个对象是要被销毁的。

```java
/**
     * Atomically adds the given value to the current value of the field of
     * the given object managed by this updater.
     *
     * @param obj An object whose field to get and set
     * @param delta the value to add
     * @return the previous value
     */
    public int getAndAdd(T obj, int delta) {
        int prev, next;
        do {
            prev = get(obj);
            next = prev + delta;
        } while (!compareAndSet(obj, prev, next));
        return prev;
    }
```

第14行为CAS操作，比较且赋值，以原子的形式改变一个变量，如果当前值等于期望的值，则更新这个变量。直到更新成功，才会返回true。compareAndSet方法位于```AtomicIntegerFieldUpdater```类中。

### AtomicIntegerFieldUpdater

1. 更新器更新的必须是int类型变量，不能是其包装类型
2. 更新器更新的必须是volatile类型变量，确保线程之间共享变量时的立即可见性
3. 变量不能是static的，必须是实例变量，因为Unsafe.objectFieldOffset()方法不支持静态变量（CAS操作本质上是通过对象实例的偏移量来直接进行赋值）
4. 更新器只能修改它可见范围内的变量，因为更新器是通过反射来得到这个变量，如果变量不可见就会报错

若要更新的变量是包装类型，可以使用AtomicReferenceFieldUpdater来进行更新

```java
package com.asuna.netty.bytebuf;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class AtomicUpdaterTest {
    public static void main(String[] args) {
//        Person person = new Person();
//        for (int i = 0; i < 10; i++) {
//            Thread thread = new Thread(()->{
//                try {
//                    Thread.sleep(50);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//                System.out.println(person.age++);
//            });
//            thread.start();
//        }
        AtomicIntegerFieldUpdater<Person> atomicIntegerFieldUpdater = AtomicIntegerFieldUpdater.newUpdater(Person.class, "age");
        Person person = new Person();
        for (int i = 0; i < 10; i++) {
            Thread thread = new Thread(()->{
                try {
                    Thread.sleep(50);

                } catch (InterruptedException e) {
                    e.printStackTrace();
                } System.out.println(atomicIntegerFieldUpdater.getAndIncrement(person));
            });
            thread.start();
        }
    }
}
class Person{
    volatile int age = 1;
}

```

### 引用计数

所有新创建的对象的引用计数都为1.当释放引用对象时，引用计数会减1，当引用计数为0时，对象会被销毁。使用buffer.duplicate()方法不会单独创建一段内存区，而是和父buffer共享同一个引用计数 。当其他方法需要释放子buffer时，在release之前一定要先将父buffer，retain一下，不然父buffer也会被销毁。

## netty编解码器和入站出站处理器

### netty处理器的概念

1. 分为两类：入站处理器和出站处理器
2. 入站处理器的顶层时ChannelInBoundHandler，出站时ChannelOutboundHandler
3. 数据处理时常用的各种编解码器本质上都是处理器

### 编码器

无论向网络中写入的数据是什么类型（int，char，String等），数据在网络中传递时，器都是以字节流的形式呈现的；将数据由原本的形式转换为字节流的操作称为编码（encode）。本质上是一种出站处理器。ChannelOutboundHandler

### 解码器

将数据由字节转换为它原本的格式或是其他格式的操作称为解码（decode），编解码统一称为codec。解码本质上是一种入站处理器。ChannelInboundHandler。

在netty中，编码器通常以```XXXEncoder```命名；解码器通常以```XXXDecoder```命名

### 自定义编解码器

如要实现自己的解码器，可以继承ByteToMessageDecoder，并实现其decode方法

```java
public class MyByteToLongDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        System.out.println("decode invoke");
        System.out.println(in.readableBytes());
        if (in.readableBytes() >= 8){
            out.add(in.readLong());
        }
    }
}
```

因为要实现字节到long类型的解码，因此需要在每次解码前判断ByteBuf里面是否有8个字节可供解码。

如要实现自己的编码器可以继承MessageToByteEncoder\<I>类，泛型I为待编码的类型。并实现其中的encode方法。

```java
public class MyLongToByteEncoder extends MessageToByteEncoder<Long> {

    @Override
    protected void encode(ChannelHandlerContext ctx, Long msg, ByteBuf out) throws Exception {
        System.out.println("encode invoke");
        System.out.println(msg);
        out.writeLong(msg);
    }
}
```

1. 无论是编码器还是解码器，其所接收的消息类型必须要与待处理的参数类型一致，否则该编解码器不会被执行
2. 在编解码器进行数据解码时，一定要记得判断缓冲（ByteBuf）中的数据是否足够用

### ReplayingDecoder解码器

```public abstract class ReplayingDecoder<S> extends ByteToMessageDecoder```

它本身继承了ByteToMessageDecoder类。用户继承这个类之后也可以做到对数据的编解码，也同样是实现decode方法

```java
public class MyByteToLongDecoder2 extends ReplayingDecoder<Void> {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        out.add(in.readLong());
    }
}
```

在使用它时，不再需要判断数据是否足够用于转码。他在里面判断了是否能够有足够长的字节来转码

```java
public class IntegerHeaderFrameDecoder extends {@link ByteToMessageDecoder} {
 *
 *   {@code @Override}
 *   protected void decode({@link ChannelHandlerContext} ctx,
 *                           {@link ByteBuf} buf, List<Object> out) throws Exception {
 *
 *     if (buf.readableBytes() > 4) {
 *        return;
 *     }
 *
 *     buf.markReaderIndex();
 *     int length = buf.readInt();
 *
 *     if (buf.readableBytes() > length) {
 *        buf.resetReaderIndex();
 *        return;
 *     }
 *
 *     out.add(buf.readBytes(length));
 *   }
 * }
```

## TCP粘包与拆包实例

```public class LengthFieldBasedFrameDecoder extends ByteToMessageDecoder```类，基于一帧长度的编解码

根据传进来的长度来分割，传递二进制数据时特别有用。例如传入消息长度为12

```java
BEFORE DECODE (14 bytes)         AFTER DECODE (12 bytes)
* +--------+----------------+      +----------------+
* | Length | Actual Content |----->| Actual Content |
* | 0x000C | "HELLO, WORLD" |      | "HELLO, WORLD" |
* +--------+----------------+      +----------------+
```

### 粘包问题

服务端MyServerInitializer代码

```java
public class MyServerInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        pipeline.addLast(new MyServerHandler());
    }
}
```

handler代码

```java
public class MyServerHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private int count = 0;
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        byte[] buffer = new byte[msg.readableBytes()];
        msg.readBytes(buffer);
        String message = new String(buffer, Charset.forName("utf-8"));
        System.out.println("server recv:" + message);
        System.out.println("server count:" + (++count));

        ByteBuf response = Unpooled.copiedBuffer(UUID.randomUUID().toString(), Charset.forName("utf-8"));
        ctx.writeAndFlush(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        ctx.close();
    }
}
```

客户端MyClientInitializer代码：

```java
public class MyClientInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new MyClientHandler());
    }
}
```

handler代码：

```java
public class MyClientHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private int count;
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        byte[] buffer = new byte[msg.readableBytes()];
        String message = new String(buffer, Charset.forName("utf-8"));
        System.out.println("client recv:" + message);
        System.out.println("client count" + ++count);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        ctx.close();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        for (int i = 0; i < 10; i++) {
            ByteBuf buffer = Unpooled.copiedBuffer("send from client:", Charset.forName("utf-8"));
            ctx.writeAndFlush(buffer);
        }
        ctx.writeAndFlush("active");
    }
}
```

此时服务端只能收到1条数据，因为传输的是字符流，服务端会把这些字符流看成一条数据。所以客户端也只能收到1条，因此必须手动处理拆包和粘包

### 拆包解决方案

自定义协议：```PersonProtocol```

```java
public class PersonProtocol {
    private int length;
    private byte[] content;

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }
}
```

然后再自定义编解码器：

解码器```public class MyPersonDecoder extends ReplayingDecoder<Void>```

```java
public class MyPersonDecoder extends ReplayingDecoder<Void> {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        int length = in.readInt();
        byte[] content = new byte[length];
        in.readBytes(content);
        PersonProtocol protocol = new PersonProtocol();
        protocol.setContent(content);
        protocol.setLength(length);
    }
}
```

编码器```public class MyPeronEncoder extends MessageToByteEncoder<PersonProtocol>```

```java
public class MyPeronEncoder extends MessageToByteEncoder<PersonProtocol> {
    @Override
    protected void encode(ChannelHandlerContext ctx, PersonProtocol msg, ByteBuf out) throws Exception {
        out.writeInt(msg.getLength());
        out.writeBytes(msg.getContent());
    }
}
```

