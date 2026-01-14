package open.vincentf13.exchange.test.client.utils;

import feign.Client;
import feign.Request;
import feign.Response;
import io.restassured.RestAssured;
import io.restassured.response.ResponseBody;
import io.restassured.specification.RequestSpecification;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class RestAssuredFeignClient implements Client {

    @Override
    public Response execute(Request request, Request.Options options) throws IOException {
        RequestSpecification spec = RestAssured.given();
        request.headers().forEach((name, values) -> values.forEach(value -> spec.header(name, value)));
        if (request.body() != null) {
            spec.body(request.body());
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
            .body(payload, StandardCharsets.UTF_8)
            .request(request)
            .build();
    }
}
