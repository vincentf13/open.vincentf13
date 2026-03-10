package open.vincentf13.exchange.test.client.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Feign;
import feign.RequestInterceptor;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.cloud.openfeign.support.SpringMvcContract;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class FeignClientSupport {
    private static final Encoder ENCODER = new JacksonEncoder(new ObjectMapper().findAndRegisterModules());
    private static final Decoder DECODER = new JacksonDecoder(new ObjectMapper().findAndRegisterModules());
    private static final SpringMvcContract CONTRACT = new SpringMvcContract();

    private FeignClientSupport() {
    }

    public static <T> T buildClient(Class<T> type, String baseUrl) {
        return Feign.builder()
            .client(new RestAssuredFeignClient())
            .encoder(ENCODER)
            .decoder(DECODER)
            .contract(CONTRACT)
            .target(type, baseUrl);
    }

    public static <T> T buildClient(Class<T> type, String baseUrl, String token) {
        RequestInterceptor auth = template -> template.header("Authorization", "Bearer " + token);
        return Feign.builder()
            .client(new RestAssuredFeignClient())
            .encoder(ENCODER)
            .decoder(DECODER)
            .contract(CONTRACT)
            .requestInterceptor(auth)
            .target(type, baseUrl);
    }

    public static <T> T assertSuccess(OpenApiResponse<T> response, String action) {
        assertNotNull(response, action + " response missing");
        assertTrue(response.isSuccess(), action + " failed: " + response.message());
        return response.data();
    }
}
