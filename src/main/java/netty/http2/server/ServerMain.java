package netty.http2.server;


import netty.http2.server.handlers.HelloWorldHandler;
import netty.http2.server.handlers.RootHandler;

public class ServerMain {
    public static void main(String[] args) throws Exception {
        Http2Server http2Server = Http2Server.Builder.newInstance()
                .setIp("192.168.0.106")
                .setPort(8080)
                .addContextHandler("/helloworld", new HelloWorldHandler())
                .addContextHandler("/", new RootHandler())
                .build();

        http2Server.startServer();
    }
}
