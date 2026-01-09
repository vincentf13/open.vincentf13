package open.vincentf13.exchange.user.sdk.rest.api;

import jakarta.validation.Valid;
import open.vincentf13.exchange.user.sdk.rest.api.dto.UserRegisterRequest;
import open.vincentf13.exchange.user.sdk.rest.api.dto.UserResponse;
import open.vincentf13.sdk.auth.auth.Jwt;
import open.vincentf13.sdk.auth.auth.PrivateAPI;
import open.vincentf13.sdk.auth.auth.PublicAPI;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

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
