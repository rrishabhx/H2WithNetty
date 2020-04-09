package netty.http2.client;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import java.util.HashMap;
import java.util.Map;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class Http2Request {
    private final String uriContext;
    private final String requestData;
    private final HashMap<String, String> headersMap;
    private final HashMap<String, String> queryParamsMap;
    private final HttpMethod httpMethod;

    public Http2Request(Builder builder) {
        this.httpMethod = builder.httpMethod;
        this.uriContext = builder.uriContext;
        this.requestData = builder.requestData;
        this.headersMap = builder.headersMap;
        this.queryParamsMap = builder.queryParamsMap;
    }

    public FullHttpRequest createRequest() {
        FullHttpRequest request;
        if (httpMethod.equals(GET) || httpMethod.equals(DELETE)) {
            request = new DefaultFullHttpRequest(HTTP_1_1, httpMethod, uriContext, Unpooled.EMPTY_BUFFER);
        } else {
            request = new DefaultFullHttpRequest(HTTP_1_1, httpMethod, uriContext,
                    wrappedBuffer(requestData.getBytes(CharsetUtil.UTF_8)));
        }

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

    public static class Builder {
        private HttpMethod httpMethod;
        private String uriContext;
        private String requestData;
        private HashMap<String, String> headersMap;
        private HashMap<String, String> queryParamsMap;


        private Builder() {
            uriContext = "/";
            headersMap = new HashMap<>();
            queryParamsMap = new HashMap<>();
        }

        public static Http2Request.Builder newInstance() {
            return new Http2Request.Builder();
        }

        public Http2Request.Builder setHttpMethod(HttpMethod httpMethod) {
            this.httpMethod = httpMethod;
            return this;
        }

        public Http2Request.Builder setUriContext(String uriContext) {
            this.uriContext = uriContext;
            return this;
        }

        public Http2Request.Builder setRequestData(String requestData) {
            if (httpMethod.equals(POST) || httpMethod.equals(PUT)) {
                this.requestData = requestData;
            }
            return this;
        }

        public Http2Request.Builder addHeader(String key, String value) {
            headersMap.put(key, value);
            return this;
        }

        public Http2Request.Builder addHeadersMap(HashMap<String, String> inputHeadersMap) {
            headersMap.putAll(inputHeadersMap);
            return this;
        }

        public Http2Request.Builder addQueryParam(String key, String value) {
            queryParamsMap.put(key, value);
            return this;
        }

        public Http2Request.Builder addQueryParamsMap(HashMap<String, String> inputQueryParamsMap) {
            queryParamsMap.putAll(inputQueryParamsMap);
            return this;
        }

        public Http2Request build() {
            return new Http2Request(this);
        }

    }
}
