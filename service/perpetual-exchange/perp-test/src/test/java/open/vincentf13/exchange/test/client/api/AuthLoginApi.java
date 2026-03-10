package open.vincentf13.exchange.test.client.api;

import jakarta.validation.Valid;
import open.vincentf13.sdk.auth.server.controller.dto.JwtTokenPair;
import open.vincentf13.sdk.auth.server.controller.dto.LoginRequest;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

public interface AuthLoginApi {

    @PostMapping("/login")
    OpenApiResponse<JwtTokenPair> login(@Valid @RequestBody LoginRequest request);
}
