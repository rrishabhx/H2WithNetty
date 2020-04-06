package netty.http2.server;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.json.JSONObject;

import java.util.HashMap;

public final class ServerUtil {
    private static HashMap<String, H2ContextHandler> contextHandlerMap;


    public static HashMap<String, H2ContextHandler> getContextHandlerMap() {
        return contextHandlerMap;
    }

    public static void setContextHandlerMap(HashMap<String, H2ContextHandler> contextHandlerMap) {
        ServerUtil.contextHandlerMap = contextHandlerMap;
    }

    public static void printAllHttpHeadersAndParams(HttpMethod httpMethod, HashMap<String, String> headersMap, HashMap<String, String> queryParamsMap) {
        try {
            // Getting Headers from request
            JSONObject headersJson = new JSONObject(headersMap);

            // Getting Params from request
            JSONObject paramsJson = new JSONObject(queryParamsMap);

            System.out.println(("HTTP REQUEST HEADERS/PARAMS: " + new JSONObject() {{
                put("METHOD", httpMethod.asciiName());
                put("HEADERS", headersJson);
                put("PARAMETERS", paramsJson);
            }}));

        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    private String[] decodePathTokens(String uri) {
        // Need to split the original URI (instead of QueryStringDecoder#path) then decode the tokens (components),
        // otherwise /test1/123%2F456 will not match /test1/:p1

        int qPos = uri.indexOf("?");
        String encodedPath = (qPos >= 0) ? uri.substring(0, qPos) : uri;

        String[] encodedTokens = encodedPath.split("/");

        String[] decodedTokens = new String[encodedTokens.length];
        for (int i = 0; i < encodedTokens.length; i++) {
            String encodedToken = encodedTokens[i];
            decodedTokens[i] = QueryStringDecoder.decodeComponent(encodedToken);
        }

        return decodedTokens;
    }

}
