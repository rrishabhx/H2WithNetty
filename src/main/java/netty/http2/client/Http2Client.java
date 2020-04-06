package netty.http2.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.handler.ssl.*;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.CharsetUtil;

import javax.net.ssl.SSLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;


public final class Http2Client {

    private final String serverIp;
    private final int serverPort;
    private final String uriContext;
    private final String requestData;
    private final HashMap<String, String> headersMap;
    private final HashMap<String, String> queryParamsMap;
    private final boolean sslSupport;
    private final HttpMethod httpMethod;
    private final int responseTimeout;
    private final HttpResponseHandler responseHandler;
    private AtomicInteger atomicStreamId;

    public Http2Client(Builder builder) {
        this.httpMethod = builder.httpMethod;
        this.serverIp = builder.serverIp;
        this.serverPort = builder.serverPort;
        this.uriContext = builder.uriContext;
        this.requestData = builder.requestData;
        this.headersMap = builder.headersMap;
        this.queryParamsMap = builder.queryParamsMap;
        this.sslSupport = builder.sslSupport;
        this.responseTimeout = builder.responseTimeout;
        this.responseHandler = builder.responseHandler;
        this.atomicStreamId = new AtomicInteger(3);
    }


    public void startClient() throws Exception {
        // Configure SSL.
        final SslContext sslCtx = getSslCtx();

        EventLoopGroup workerGroup = new NioEventLoopGroup();
        Http2ClientInitializer initializer = new Http2ClientInitializer(sslCtx, Integer.MAX_VALUE, responseHandler);

        try {
            // Configure the client.
            Bootstrap b = new Bootstrap();
            b.group(workerGroup);
            b.channel(NioSocketChannel.class);
            b.option(ChannelOption.SO_KEEPALIVE, true);
            b.remoteAddress(serverIp, serverPort);
            b.handler(initializer);

            // Start the client.
            Channel channel = b.connect().syncUninterruptibly().channel();
            System.out.println("Connected to [" + serverIp + ':' + serverPort + ']');

            // Wait for the HTTP/2 upgrade to occur.
            Http2SettingsHandler http2SettingsHandler = initializer.settingsHandler();
            http2SettingsHandler.awaitSettings(responseTimeout, TimeUnit.SECONDS);

            HttpResponseHandler responseHandler = initializer.responseHandler();
            int streamId;
            streamId = atomicStreamId.intValue();


            FullHttpRequest request = createHttp2Request();
            responseHandler.put(streamId, channel.write(request), channel.newPromise());
            channel.flush();
            responseHandler.awaitResponses(responseTimeout, TimeUnit.SECONDS);

            System.out.println("Finished HTTP/2 request(s)");

            // Wait until the connection is closed.
            channel.close().syncUninterruptibly();
        } finally {
            workerGroup.shutdownGracefully();
        }
    }

    private FullHttpRequest createHttp2Request() {
        HttpScheme scheme = sslSupport ? HttpScheme.HTTPS : HttpScheme.HTTP;

        FullHttpRequest request;
        if (httpMethod.equals(GET) || httpMethod.equals(DELETE)) {
            request = new DefaultFullHttpRequest(HTTP_1_1, httpMethod, uriContext, Unpooled.EMPTY_BUFFER);
        } else {
            request = new DefaultFullHttpRequest(HTTP_1_1, httpMethod, uriContext,
                    wrappedBuffer(requestData.getBytes(CharsetUtil.UTF_8)));
        }

        request.headers().add(HttpHeaderNames.HOST, serverIp + ":" + serverPort);
        request.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), scheme.name());
        request.headers().add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);
        request.headers().add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.DEFLATE);

        for (Map.Entry<String, String> entry : headersMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            request.headers().add(key, value);
        }


        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : queryParamsMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            sb.append(key).append("=").append(value).append("&");
        }
        if (!sb.toString().isEmpty()) {
            String qpStr = sb.toString();
            request.setUri(uriContext + "?" + qpStr.substring(0, qpStr.length() - 1));
        }

        return request;
    }

    private SslContext getSslCtx() {
        SslContext sslCtx = null;
        try {
            if (sslSupport) {
                SslProvider provider = OpenSsl.isAlpnSupported() ? SslProvider.OPENSSL : SslProvider.JDK;
                sslCtx = SslContextBuilder.forClient()
                        .sslProvider(provider)
                        /* NOTE: the cipher filter may not include all ciphers required by the HTTP/2 specification.
                         * Please refer to the HTTP/2 specification for cipher requirements. */
                        .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .applicationProtocolConfig(new ApplicationProtocolConfig(
                                Protocol.ALPN,
                                // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
                                SelectorFailureBehavior.NO_ADVERTISE,
                                // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
                                SelectedListenerFailureBehavior.ACCEPT,
                                ApplicationProtocolNames.HTTP_2,
                                ApplicationProtocolNames.HTTP_1_1))
                        .build();
            }
        } catch (SSLException e) {
            e.printStackTrace();
        }
        return sslCtx;
    }

    public static class Builder {
        private HttpMethod httpMethod;
        private String serverIp;
        private int serverPort;
        private String uriContext;
        private String requestData;
        private HashMap<String, String> headersMap;
        private HashMap<String, String> queryParamsMap;
        private boolean sslSupport;
        private int responseTimeout;
        private HttpResponseHandler responseHandler;

        private Builder() {
            uriContext = "/";
            headersMap = new HashMap<>();
            queryParamsMap = new HashMap<>();
            sslSupport = false;
            responseTimeout = 5;
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder setHttpMethod(HttpMethod httpMethod) {
            this.httpMethod = httpMethod;
            return this;
        }

        public Builder setServerIp(String serverIp) {
            this.serverIp = serverIp;
            ClientUtil.setServerIp(serverIp);
            return this;
        }

        public Builder setServerPort(int serverPort) {
            this.serverPort = serverPort;
            ClientUtil.setServerPort(serverPort);
            return this;
        }

        public Builder setUriContext(String uriContext) {
            this.uriContext = uriContext;
            return this;
        }

        public Builder setRequestData(String requestData) {
            if (httpMethod.equals(POST) || httpMethod.equals(PUT)) {
                this.requestData = requestData;
            }
            return this;
        }

        public Builder setSslSupport(boolean sslSupport) {
            this.sslSupport = sslSupport;
            return this;
        }

        public Builder setResponseTimeout(int responseTimeout) {
            this.responseTimeout = responseTimeout;
            return this;
        }

        public Builder setResponseHandler(HttpResponseHandler responseHandler) {
            this.responseHandler = responseHandler;
            return this;
        }

        public Builder addHeader(String key, String value) {
            headersMap.put(key, value);
            return this;
        }

        public Builder addHeadersMap(HashMap<String, String> inputHeadersMap) {
            headersMap.putAll(inputHeadersMap);
            return this;
        }

        public Builder addQueryParam(String key, String value) {
            queryParamsMap.put(key, value);
            return this;
        }

        public Builder addQueryParamsMap(HashMap<String, String> inputQueryParamsMap) {
            queryParamsMap.putAll(inputQueryParamsMap);
            return this;
        }

        public Http2Client build() {
            return new Http2Client(this);
        }

    }
}
