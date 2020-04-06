package netty.http2.server.handlers;


import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import netty.http2.server.H2ContextHandler;
import netty.http2.server.H2Response;

import java.util.HashMap;

public class HelloWorldHandler implements H2ContextHandler {

    @Override
    public H2Response handleGet(FullHttpRequest request, ChannelHandlerContext ctx, HashMap<String, String> headersMap, HashMap<String, String> queryParamsMap) {
        System.out.println("HelloWorld (GET) handler called");
        return new H2Response(404, "Hello World (GET req)\n");
    }

    @Override
    public H2Response handlePost(FullHttpRequest request, ChannelHandlerContext ctx, HashMap<String, String> headersMap, HashMap<String, String> queryParamsMap, String content) {
        System.out.println("HelloWorld (POST) handler called");
        return new H2Response(402, "Hello World (POST req)\n");
    }

    @Override
    public H2Response handlePut(FullHttpRequest request, ChannelHandlerContext ctx, HashMap<String, String> headersMap, HashMap<String, String> queryParamsMap, String content) {
        return null;
    }

    @Override
    public H2Response handleDelete(FullHttpRequest request, ChannelHandlerContext ctx, HashMap<String, String> headersMap, HashMap<String, String> queryParamsMap) {
        return null;
    }
}
