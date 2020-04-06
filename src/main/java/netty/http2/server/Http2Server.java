package netty.http2.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.*;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.util.SelfSignedCertificate;

import java.util.HashMap;

/**
 * An HTTP/2 Server that responds to requests with a Hello World. Once started, you can test the
 * server with the example client.
 */
public final class Http2Server {

    private final String ip;
    private final int port;
    private final HashMap<String, H2ContextHandler> contextHandlerHashMap;
    private final boolean sslSupport;

    public Http2Server(Builder builder) {
        this.ip = builder.ip;
        this.port = builder.port;
        this.contextHandlerHashMap = builder.contextHandlerHashMap;
        this.sslSupport = builder.sslSupport;
    }

    public void startServer() throws Exception {
        // Configure SSL.
        final SslContext sslCtx;
        if (sslSupport) {
            SslProvider provider = OpenSsl.isAlpnSupported() ? SslProvider.OPENSSL : SslProvider.JDK;
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                    .sslProvider(provider)
                    /* NOTE: the cipher filter may not include all ciphers required by the HTTP/2 specification.
                     * Please refer to the HTTP/2 specification for cipher requirements. */
                    .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                    .applicationProtocolConfig(new ApplicationProtocolConfig(
                            Protocol.ALPN,
                            // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
                            SelectorFailureBehavior.NO_ADVERTISE,
                            // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
                            SelectedListenerFailureBehavior.ACCEPT,
                            ApplicationProtocolNames.HTTP_2,
                            ApplicationProtocolNames.HTTP_1_1))
                    .build();
        } else {
            sslCtx = null;
        }

        // Configure the server.
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.option(ChannelOption.SO_BACKLOG, 1024);
            b.group(group)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new Http2ServerInitializer(sslCtx));

            // Setting context handlers
            ServerUtil.setContextHandlerMap(contextHandlerHashMap);

            Channel ch = b.bind(port).sync().channel();

            System.out.println("Open your HTTP/2-enabled web browser and navigate to " +
                    (sslSupport ? "https" : "http") + "://" + ip + ":" + port + '/');

            ch.closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }

    public static class Builder {
        private String ip;
        private int port;
        private HashMap<String, H2ContextHandler> contextHandlerHashMap;
        private boolean sslSupport;

        private Builder() {
            contextHandlerHashMap = new HashMap<>();
            sslSupport = false;
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder setIp(String ip) {
            this.ip = ip;
            return this;
        }

        public Builder setPort(int port) {
            this.port = port;
            return this;
        }

        public Builder setSetSslSupport(boolean sslSupport) {
            this.sslSupport = sslSupport;
            return this;
        }

        public Builder addContextHandler(String context, H2ContextHandler handler) {
            contextHandlerHashMap.put(context, handler);
            return this;
        }

        public Http2Server build() {
            return new Http2Server(this);
        }
    }
}