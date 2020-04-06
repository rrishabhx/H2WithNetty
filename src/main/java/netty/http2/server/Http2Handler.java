package netty.http2.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http2.*;
import io.netty.util.CharsetUtil;
import netty.http2.server.handlers.RootHandler;

import java.util.HashMap;
import java.util.Map;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.buffer.Unpooled.unreleasableBuffer;

/**
 * A simple handler that responds with the message "Hello World!".
 */
public final class Http2Handler extends Http2ConnectionHandler implements Http2FrameListener {

    static final ByteBuf RESPONSE_BYTES = unreleasableBuffer(copiedBuffer("Hello World", CharsetUtil.UTF_8));
    private String context;
    private FullHttpRequest fullHttpRequest;
    private HttpMethod httpMethod;
    private HashMap<String, String> headersMap;
    private HashMap<String, String> queryParamsMap;

    Http2Handler(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
                 Http2Settings initialSettings) {
        super(decoder, encoder, initialSettings);
    }

    private Http2Headers http1HeadersToHttp2Headers(FullHttpRequest request) {
        CharSequence host = request.headers().get(HttpHeaderNames.HOST);
        Http2Headers http2Headers = new DefaultHttp2Headers()
                .method(request.method().asciiName())
                .path(request.uri())
                .scheme(HttpScheme.HTTP.name());
        if (host != null) {
            http2Headers.authority(host);
        }

        fullHttpRequest = request;

        System.out.println("Full URI: " + request.uri() + " Method: " + request.method().asciiName());
        return http2Headers;
    }

    /**
     * Handles the cleartext HTTP upgrade event. If an upgrade occurred, sends a simple response via HTTP/2
     * on stream 1 (the stream specifically reserved for cleartext HTTP upgrade).
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof HttpServerUpgradeHandler.UpgradeEvent) {
            HttpServerUpgradeHandler.UpgradeEvent upgradeEvent =
                    (HttpServerUpgradeHandler.UpgradeEvent) evt;
            onHeadersRead(ctx, 1, http1HeadersToHttp2Headers(upgradeEvent.upgradeRequest()), 0, true);
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        cause.printStackTrace();
        ctx.close();
    }


    private void sendResponse(ChannelHandlerContext ctx, int streamId, Http2Headers responseHeaders, ByteBuf payload) {
        System.out.println("Request URI received= " + context);
        // Send a frame for the response status
        encoder().writeHeaders(ctx, streamId, responseHeaders, 0, false, ctx.newPromise());
        encoder().writeData(ctx, streamId, payload, 0, true, ctx.newPromise());
    }

    @Override
    public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) {
        int processed = data.readableBytes() + padding;
        String requestContent = data.toString(CharsetUtil.UTF_8);

        if (endOfStream) {
            if (httpMethod.equals(HttpMethod.POST) || httpMethod.equals(HttpMethod.PUT)) {
                H2Response h2Response = assignUriToHandler(context, httpMethod, fullHttpRequest, ctx, headersMap,
                        queryParamsMap, requestContent);

                sendResponse(ctx, streamId, h2Response.getResponseHeaders(), h2Response.getPayload());
                ServerUtil.printAllHttpHeadersAndParams(httpMethod, headersMap, queryParamsMap);
            }
        }
        return processed;
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId,
                              Http2Headers headers, int padding, boolean endOfStream) {
        headersMap = getHeaders(headers);
        httpMethod = new HttpMethod(headers.method().toString());
        context = headers.path().toString().split("\\?")[0];
        queryParamsMap = getQueryParams(headers.path().toString());
        System.out.println("Http Methods=> " + httpMethod.asciiName() + "\tStreamID=> " + streamId
                + "\tQueryParams=> " + headers.path());

        ServerUtil.printAllHttpHeadersAndParams(httpMethod, headersMap, queryParamsMap);


        if (endOfStream) {
            if ((httpMethod.equals(HttpMethod.GET) || httpMethod.equals(HttpMethod.DELETE))) {
                H2Response h2Response = assignUriToHandler(context, httpMethod, fullHttpRequest, ctx, headersMap,
                        queryParamsMap, null);

                sendResponse(ctx, streamId, h2Response.getResponseHeaders(), h2Response.getPayload());
            }
        }
    }

    private HashMap<String, String> getHeaders(Http2Headers headers) {
        HashMap<String, String> headersMap = new HashMap<>();

        for (Map.Entry<CharSequence, CharSequence> entry : headers) {
            String key = entry.getKey().toString();
            String value = entry.getValue().toString();
            headersMap.put(key, value);
        }

        this.headersMap = headersMap;
        return headersMap;
    }

    private HashMap<String, String> getQueryParams(String pathHeader) {
        HashMap<String, String> queryParamsMap = new HashMap<>();
        String[] requestUriParts = pathHeader.split("\\?");

        if (requestUriParts.length == 1) {
            return queryParamsMap;
        }

        String[] queryParamParts = requestUriParts[1].split("&");
        for (String entry : queryParamParts) {
            String[] entryParts = entry.split("=");
            if (entryParts.length == 2) {
                String key = entryParts[0];
                String value = entryParts[1];
                queryParamsMap.put(key, value);
            }
        }

        return queryParamsMap;
    }

    private H2Response assignUriToHandler(String uri, HttpMethod method, FullHttpRequest request, ChannelHandlerContext ctx, HashMap<String, String> headersMap,
                                          HashMap<String, String> queryParamsMap, String content) {
        System.out.println("+++++++++++++++++++++++++++++");
        System.out.println("Assigning URI to handler: --> Method: " + method.asciiName() + "\tContent: " + content);
        System.out.println("+++++++++++++++++++++++++++++");

        H2ContextHandler contextHandler = ServerUtil.getContextHandlerMap().getOrDefault(uri, new RootHandler());
        H2Response h2Response = null;

        if (HttpMethod.GET.equals(method)) {
            h2Response = contextHandler.handleGet(request, ctx, headersMap, queryParamsMap);
        } else if (HttpMethod.POST.equals(method)) {
            h2Response = contextHandler.handlePost(request, ctx, headersMap, queryParamsMap, content);
        } else if (HttpMethod.PUT.equals(method)) {
            h2Response = contextHandler.handlePut(request, ctx, headersMap, queryParamsMap, content);
        } else if (HttpMethod.DELETE.equals(method)) {
            h2Response = contextHandler.handleDelete(request, ctx, headersMap, queryParamsMap);
        }

        return h2Response;
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
                              short weight, boolean exclusive, int padding, boolean endOfStream) {
        onHeadersRead(ctx, streamId, headers, padding, endOfStream);
    }

    @Override
    public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency,
                               short weight, boolean exclusive) {
    }

    @Override
    public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
    }

    @Override
    public void onSettingsAckRead(ChannelHandlerContext ctx) {
    }

    @Override
    public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) {
    }

    @Override
    public void onPingRead(ChannelHandlerContext ctx, long data) {
    }

    @Override
    public void onPingAckRead(ChannelHandlerContext ctx, long data) {
    }

    @Override
    public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                                  Http2Headers headers, int padding) {
    }

    @Override
    public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) {
    }

    @Override
    public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) {
    }

    @Override
    public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId,
                               Http2Flags flags, ByteBuf payload) {
    }
}
