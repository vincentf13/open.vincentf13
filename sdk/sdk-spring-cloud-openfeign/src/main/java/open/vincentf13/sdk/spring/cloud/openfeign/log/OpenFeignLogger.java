package open.vincentf13.sdk.spring.cloud.openfeign.log;

import feign.Request;
import feign.Response;
import feign.Util;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.spring.cloud.openfeign.FeignEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

public class OpenFeignLogger extends feign.Logger {

    @Override
    protected void log(String configKey, String format, Object... args) {
        // No-op: We handle logging in logRequest and logAndRebufferResponse
    }

    @Override
    protected void logRequest(String configKey, Level logLevel, Request request) {
        StringBuilder headers = new StringBuilder();
        if (request.headers() != null) {
            for (Map.Entry<String, Collection<String>> entry : request.headers().entrySet()) {
                headers.append(entry.getKey()).append(": ").append(entry.getValue()).append("; ");
            }
        }

        String body = "";
        if (request.body() != null) {
            body = new String(request.body(), request.charset() != null ? request.charset() : StandardCharsets.UTF_8);
        }

        OpenLog.info(FeignEvent.FEIGN_REQUEST,
                     "method", request.httpMethod(),
                     "uri", request.url(),
                     "\nReqHead", headers.toString(),
                     "\nReqBody", body);
    }

    @Override
    protected Response logAndRebufferResponse(String configKey, Level logLevel, Response response, long elapsedTime) throws IOException {
        StringBuilder headers = new StringBuilder();
        if (response.headers() != null) {
            for (Map.Entry<String, Collection<String>> entry : response.headers().entrySet()) {
                headers.append(entry.getKey()).append(": ").append(entry.getValue()).append("; ");
            }
        }

        String body = "";
        byte[] bodyData = null;
        if (response.body() != null) {
            bodyData = Util.toByteArray(response.body().asInputStream());
            body = new String(bodyData, StandardCharsets.UTF_8);
        }

        OpenLog.info(FeignEvent.FEIGN_RESPONSE,
                     "status", response.status(),
                     "duration", elapsedTime + "ms",
                     "\nResHead", headers.toString(),
                     "\nResBody", body);

        if (bodyData != null) {
            return response.toBuilder().body(bodyData).build();
        }
        return response;
    }
}
