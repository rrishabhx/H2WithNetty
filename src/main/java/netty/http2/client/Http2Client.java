package netty.http2.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.handler.ssl.*;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import javax.net.ssl.SSLException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


public final class Http2Client {

    private final String serverIp;
    private final int serverPort;
    private final boolean sslSupport;
    private final int responseTimeout;
    private final HttpResponseHandler responseHandler;
    private Channel channel;
    private AtomicInteger streamId;

    public Http2Client(Builder builder) {
        this.serverIp = builder.serverIp;
        this.serverPort = builder.serverPort;
        this.sslSupport = builder.sslSupport;
        this.responseTimeout = builder.responseTimeout;
        this.responseHandler = builder.responseHandler;
        this.channel = null;
        this.streamId = new AtomicInteger(3);
    }

    public Http2Client initClient() throws Exception {
        // Configure SSL.
        final SslContext sslCtx = getSslCtx();

        EventLoopGroup workerGroup = new NioEventLoopGroup();
        Http2ClientInitializer initializer = new Http2ClientInitializer(sslCtx, Integer.MAX_VALUE, responseHandler);

        // Configure the client.
        Bootstrap b = new Bootstrap();
        b.group(workerGroup);
        b.channel(NioSocketChannel.class);
        b.option(ChannelOption.SO_KEEPALIVE, true);
        b.remoteAddress(serverIp, serverPort);
        b.handler(initializer);

        // Start the client.
        channel = b.connect().syncUninterruptibly().channel();
        System.out.println("Connected to [" + serverIp + ':' + serverPort + ']');

        // Wait for the HTTP/2 upgrade to occur.
        Http2SettingsHandler http2SettingsHandler = initializer.settingsHandler();
        http2SettingsHandler.awaitSettings(responseTimeout, TimeUnit.SECONDS);

        return this;
    }


    public void sendRequest(FullHttpRequest request) {
        HttpScheme scheme = sslSupport ? HttpScheme.HTTPS : HttpScheme.HTTP;
        System.out.println("Sending request with StreamId= " + streamId.get());

        request.headers().add(HttpHeaderNames.HOST, serverIp + ":" + serverPort);
        request.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), scheme.name());

        responseHandler.put(streamId.getAndAdd(2), channel.write(request), channel.newPromise());
        channel.flush();
        responseHandler.awaitResponses(responseTimeout, TimeUnit.SECONDS);

        System.out.println("Finished HTTP/2 request(s)");
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
        private String serverIp;
        private int serverPort;
        private boolean sslSupport;
        private int responseTimeout;
        private HttpResponseHandler responseHandler;

        private Builder() {
            sslSupport = false;
            responseTimeout = 5;
        }

        public static Builder newInstance() {
            return new Builder();
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

        public Http2Client build() {
            return new Http2Client(this);
        }

    }
}
