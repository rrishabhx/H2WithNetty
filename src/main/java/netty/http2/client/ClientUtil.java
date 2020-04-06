package netty.http2.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.util.CharsetUtil;

import java.util.Map;

public class ClientUtil {
    private static String serverIp;
    private static int serverPort;
    private static Map<Integer, Map.Entry<ChannelFuture, ChannelPromise>> streamidPromiseMap;

    public static void setStreamidPromiseMap(Map<Integer, Map.Entry<ChannelFuture, ChannelPromise>> streamidPromiseMap) {
        ClientUtil.streamidPromiseMap = streamidPromiseMap;
    }

    public static String getServerIp() {
        return serverIp;
    }

    public static void setServerIp(String serverIp) {
        ClientUtil.serverIp = serverIp;
    }

    public static int getServerPort() {
        return serverPort;
    }

    public static void setServerPort(int serverPort) {
        ClientUtil.serverPort = serverPort;
    }

    public static H2ResponseFromServer parseResponseFromServer(ChannelHandlerContext ctx, FullHttpResponse msg)
            throws Exception {
        H2ResponseFromServer h2Response = new H2ResponseFromServer();

        Integer streamId = msg.headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text());
        if (streamId == null) {
            System.err.println("TestHttpResponseHandler unexpected message received: " + msg);
            return h2Response;
        }

        Map.Entry<ChannelFuture, ChannelPromise> entry = streamidPromiseMap.get(streamId);
        if (entry == null) {
            System.err.println("Message received for unknown stream id " + streamId);
        } else {
            // Do stuff with the message (for now just print it)
            ByteBuf content = msg.content();
            if (content.isReadable()) {
                int contentLength = content.readableBytes();
                byte[] arr = new byte[contentLength];
                content.readBytes(arr);

                // Response content and status code
                int responseStatusCode = msg.status().code();
                h2Response.setResponseStatus(responseStatusCode);

                String responseContent = new String(arr, 0, contentLength, CharsetUtil.UTF_8);
                h2Response.setResponseMsg(responseContent);
            }
            entry.getValue().setSuccess();
        }

        return h2Response;
    }
}
