package netty.http2.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.CharsetUtil;

public class H2Response {
    private Http2Headers responseHeaders;
    private ByteBuf payload;

    public H2Response(int responseStatus, String payloadData) {
        this.responseHeaders = new DefaultHttp2Headers().status(HttpResponseStatus.valueOf(responseStatus).codeAsText());
        this.payload = Unpooled.wrappedBuffer(payloadData.getBytes(CharsetUtil.UTF_8));
    }

    public Http2Headers getResponseHeaders() {
        return responseHeaders;
    }

    public ByteBuf getPayload() {
        return payload;
    }
}
