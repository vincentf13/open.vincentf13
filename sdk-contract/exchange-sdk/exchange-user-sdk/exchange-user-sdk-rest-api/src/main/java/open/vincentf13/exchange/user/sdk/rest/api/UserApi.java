package open.vincentf13.exchange.user.sdk.rest.api;

import jakarta.validation.Valid;
import open.vincentf13.exchange.user.sdk.rest.api.dto.UserRegisterRequest;
import open.vincentf13.exchange.user.sdk.rest.api.dto.UserResponse;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import open.vincentf13.sdk.spring.security.auth.Jwt;
import open.vincentf13.sdk.spring.security.auth.PrivateAPI;
import open.vincentf13.sdk.spring.security.auth.PublicAPI;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
public interface UserApi {

    @PostMapping
    @PublicAPI
    OpenApiResponse<UserResponse> register(@Valid @RequestBody UserRegisterRequest request);

    @GetMapping("/me")
    @Jwt
    OpenApiResponse<UserResponse> getMe();

    @GetMapping("/by-email")
    @PrivateAPI
    OpenApiResponse<UserResponse> findByEmail(@RequestParam("email") String email);

}
