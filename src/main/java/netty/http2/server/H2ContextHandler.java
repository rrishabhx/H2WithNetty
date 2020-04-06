package netty.http2.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

import java.util.HashMap;

public interface H2ContextHandler {
    H2Response handleGet(FullHttpRequest request, ChannelHandlerContext ctx, HashMap<String, String> headersMap,
                         HashMap<String, String> queryParamsMap);

    H2Response handlePost(FullHttpRequest request, ChannelHandlerContext ctx, HashMap<String, String> headersMap,
                          HashMap<String, String> queryParamsMap, String content);

    H2Response handlePut(FullHttpRequest request, ChannelHandlerContext ctx, HashMap<String, String> headersMap,
                         HashMap<String, String> queryParamsMap, String content);

    H2Response handleDelete(FullHttpRequest request, ChannelHandlerContext ctx, HashMap<String, String> headersMap,
                            HashMap<String, String> queryParamsMap);
}
