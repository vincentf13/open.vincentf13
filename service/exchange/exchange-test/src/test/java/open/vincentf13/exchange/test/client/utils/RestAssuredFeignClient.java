package open.vincentf13.exchange.test.client.utils;

import feign.Client;
import feign.Request;
import feign.Response;
import io.restassured.RestAssured;
import io.restassured.response.ResponseBody;
import io.restassured.specification.RequestSpecification;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class RestAssuredFeignClient implements Client {

    @Override
    public Response execute(Request request, Request.Options options) throws IOException {
        RequestSpecification spec = RestAssured.given();

        // Debug Log
        System.out.println("[RestAssuredFeignClient] Executing " + request.httpMethod() + " " + request.url());
        if (request.body() != null) {
            System.out.println("[RestAssuredFeignClient] Body length: " + request.body().length);
        } else {
            System.out.println("[RestAssuredFeignClient] Body is null");
        }
        request.headers().forEach((k, v) -> System.out.println("[RestAssuredFeignClient] Header: " + k + " = " + v));

        // 1. 設定 Headers (尋找 Content-Type)
        String contentType = null;
        for (Map.Entry<String, Collection<String>> entry : request.headers().entrySet()) {
            String name = entry.getKey();
            if ("Content-Type".equalsIgnoreCase(name)) {
                Collection<String> values = entry.getValue();
                if (values != null && !values.isEmpty()) {
                    contentType = values.iterator().next();
                    spec.contentType(contentType);
                }
            } else if (!"Content-Length".equalsIgnoreCase(name)) {
                entry.getValue().forEach(value -> spec.header(name, value));
            }
        }

        // 2. 預設 Content-Type (若未設定且有 Body，預設為 JSON)
        if (contentType == null && request.body() != null) {
            System.out.println("[RestAssuredFeignClient] Content-Type missing, defaulting to application/json");
            contentType = "application/json";
            spec.contentType(contentType);
        }

        // 3. 設定 Body
        if (request.body() != null) {
            if (contentType != null && contentType.toLowerCase().contains("json")) {
                java.nio.charset.Charset charset = request.charset();
                if (charset == null) {
                    charset = java.nio.charset.StandardCharsets.UTF_8;
                }
                spec.body(new String(request.body(), charset));
            } else {
                spec.body(request.body());
            }
        }

        io.restassured.response.Response response = switch (request.httpMethod()) {
            case GET -> spec.get(request.url());
            case POST -> spec.post(request.url());
            case PUT -> spec.put(request.url());
            case DELETE -> spec.delete(request.url());
            case PATCH -> spec.patch(request.url());
            case HEAD -> spec.head(request.url());
            case OPTIONS -> spec.options(request.url());
            case TRACE -> spec.request("TRACE", request.url());
            default -> spec.request(request.httpMethod().name(), request.url());
        };

        Map<String, Collection<String>> headers = new LinkedHashMap<>();
        response.getHeaders().forEach(header ->
            headers.computeIfAbsent(header.getName(), key -> new ArrayList<>())
                .add(header.getValue()));

        ResponseBody<?> body = response.getBody();
        byte[] payload = body == null ? new byte[0] : body.asByteArray();

        return Response.builder()
            .status(response.statusCode())
            .reason(response.statusLine())
            .headers(headers)
            .body(new java.io.ByteArrayInputStream(payload), payload.length)
            .request(request)
            .build();
    }
}
