package netty.http2.server.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import netty.http2.server.H2ContextHandler;
import netty.http2.server.H2Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.util.HashMap;

public class RootHandler implements H2ContextHandler {
    private static final Logger logger = LogManager.getLogger(RootHandler.class);

    @Override
    public H2Response handleGet(FullHttpRequest request, ChannelHandlerContext ctx, HashMap<String, String> headersMap, HashMap<String, String> queryParamsMap) {
        logger.warn("Root (GET) handler called");
        logger.warn("Headers: " + new JSONObject(headersMap));
        logger.warn("Parameters: " + new JSONObject(queryParamsMap));
        return new H2Response(200, "GET Handling response sent (GET req)\n");
    }

    @Override
    public H2Response handlePost(FullHttpRequest request, ChannelHandlerContext ctx, HashMap<String, String> headersMap, HashMap<String, String> queryParamsMap, String content) {
        logger.warn("Root (POST) handler called");
        logger.warn("Content received: " + content);
        logger.warn("Headers: " + new JSONObject(headersMap));
        logger.warn("Parameters: " + new JSONObject(queryParamsMap));
        return new H2Response(200, "POST Handling response sent (POST req)\n");
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
