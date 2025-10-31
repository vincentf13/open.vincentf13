package open.vincentf13.exchange.user.api;

import jakarta.validation.Valid;
import open.vincentf13.common.spring.mvc.OpenApiResponse;
import open.vincentf13.exchange.user.api.dto.UserRegisterRequest;
import open.vincentf13.exchange.user.api.dto.UserUpdateStatusRequest;
import open.vincentf13.exchange.user.api.dto.UserResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RequestMapping("/api/users")
public interface UserApi {

    @PostMapping
    OpenApiResponse<UserResponse> register(@Valid @RequestBody UserRegisterRequest request);

    @GetMapping("/me")
    OpenApiResponse<UserResponse> getMe();

    @PatchMapping("/me")
    OpenApiResponse<UserResponse> updateMe(@Valid @RequestBody UserUpdateStatusRequest request);
}
