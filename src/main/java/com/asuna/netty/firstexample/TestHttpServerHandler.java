package com.asuna.netty.firstexample;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import java.net.URI;

/**
 * 自己的HTTP处理器，负责处理逻辑
 */
public class TestHttpServerHandler extends SimpleChannelInboundHandler<HttpObject> {

    //处理请求，构造响应在这个方法中完成
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        System.out.println(msg.getClass());
        if (msg instanceof HttpRequest){

            HttpRequest httpRequest = (HttpRequest) msg;
            System.out.println("请求方法名:" + httpRequest.method().name());
            URI uri = new URI(httpRequest.uri());
            if ("/favicon.ico".equals(uri.getPath())){
                System.out.println("favicon.ico");
                return;
            }
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

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("channel active");
        super.channelActive(ctx);
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        System.out.println("channel registered");
        super.channelRegistered(ctx);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        System.out.println("handler added");
        super.handlerAdded(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("channel inactive");
        super.channelInactive(ctx);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        System.out.println("channel unregistered");
        super.channelUnregistered(ctx);
    }
}
