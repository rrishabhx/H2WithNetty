package netty.http2.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;

import java.util.HashMap;

public class ClientMain {
    public static void main(String[] args) throws Exception {
        Http2Client http2Client = Http2Client.Builder.newInstance()
                .setServerIp("192.168.0.106")
                .setServerPort(8080)
                .setResponseHandler(new HttpResponseHandler() {
                    @Override
                    public void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception {
                        H2ResponseFromServer responseFromServer = ClientUtil.parseResponseFromServer(ctx, msg);
                        System.out.println("Anonymous response: " + responseFromServer.getResponseStatus() + "\t"
                                + responseFromServer.getResponseMsg());
                    }
                })
                .build().initClient();


        FullHttpRequest fullHttpRequest = Http2Request.Builder.newInstance()
                .setHttpMethod(HttpMethod.POST)
                .setRequestData("Hello there everyone")
                .setUriContext("/helloworld")
                .addHeader("headerOne", "one")
                .addHeadersMap(new HashMap<String, String>() {{
                    put("headerTwo", "two");
                    put("headerThree", "three");
                }})
                .addQueryParam("paramOne", "one")
                .addQueryParamsMap(new HashMap<String, String>() {{
                    put("paramTwo", "two");
                    put("paramThree", "three");
                }})
                .build().createRequest();


        http2Client.sendRequest(fullHttpRequest);
        http2Client.sendRequest(fullHttpRequest);


    }
}
