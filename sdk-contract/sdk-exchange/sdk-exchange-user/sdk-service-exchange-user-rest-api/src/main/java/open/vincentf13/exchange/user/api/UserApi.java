package open.vincentf13.exchange.user.api;

import jakarta.validation.Valid;
import open.vincentf13.common.spring.mvc.OpenApiResponse;
import open.vincentf13.exchange.user.api.dto.RegisterUserRequest;
import open.vincentf13.exchange.user.api.dto.UpdateUserStatusRequest;
import open.vincentf13.exchange.user.api.dto.UserResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RequestMapping("/api/users")
public interface UserApi {

    @PostMapping
    OpenApiResponse<UserResponse> register(@Valid @RequestBody RegisterUserRequest request);

    @GetMapping("/{id}")
    OpenApiResponse<UserResponse> getById(@PathVariable("id") Long id);

    @GetMapping
    OpenApiResponse<?> find(@RequestParam(value = "email", required = false) String email);

    @PatchMapping("/{id}/status")
    OpenApiResponse<UserResponse> updateStatus(@PathVariable("id") Long id,
                                               @Valid @RequestBody UpdateUserStatusRequest request);
}
