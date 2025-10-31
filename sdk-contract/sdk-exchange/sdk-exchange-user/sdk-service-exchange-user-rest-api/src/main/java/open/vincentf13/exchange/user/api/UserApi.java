package open.vincentf13.exchange.user.api;

import jakarta.validation.Valid;
import open.vincentf13.common.spring.mvc.OpenApiResponse;
import open.vincentf13.exchange.user.api.dto.UserRegisterRequest;
import open.vincentf13.exchange.user.api.dto.UserResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RequestMapping("/api/users")
public interface UserApi {

    @PostMapping
    OpenApiResponse<UserResponse> register(@Valid @RequestBody UserRegisterRequest request);

    @GetMapping("/me")
    OpenApiResponse<UserResponse> getMe();

    @GetMapping("/{id}")
    OpenApiResponse<UserResponse> findById(@PathVariable("id") Long id);

    @GetMapping("/by-email")
    OpenApiResponse<UserResponse> findByEmail(@RequestParam("email") String email);

}
