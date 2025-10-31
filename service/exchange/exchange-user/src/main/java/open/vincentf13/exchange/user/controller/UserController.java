package open.vincentf13.exchange.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import open.vincentf13.common.spring.mvc.OpenApiResponse;
import open.vincentf13.exchange.user.dto.RegisterUserRequest;
import open.vincentf13.exchange.user.dto.UpdateUserStatusRequest;
import open.vincentf13.exchange.user.dto.UserResponse;
import open.vincentf13.exchange.user.service.UserApplicationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@Validated
@RequiredArgsConstructor
public class UserController {

    private final UserApplicationService userService;

    @PostMapping
    public OpenApiResponse<UserResponse> register(@RequestBody @Valid RegisterUserRequest request) {
        return OpenApiResponse.success(userService.register(request));
    }

    @GetMapping("/{id}")
    public OpenApiResponse<UserResponse> getById(@PathVariable Long id) {
        return OpenApiResponse.success(userService.findById(id));
    }

    @GetMapping
    public OpenApiResponse<?> find(@RequestParam(value = "email", required = false) String email) {
        if (email != null && !email.isBlank()) {
            return OpenApiResponse.success(userService.findByEmail(email));
        }
        return OpenApiResponse.success(userService.listAll());
    }

    @PatchMapping("/{id}/status")
    public OpenApiResponse<UserResponse> updateStatus(@PathVariable Long id,
                                                      @RequestBody @Valid UpdateUserStatusRequest request) {
        return OpenApiResponse.success(userService.updateStatus(id, request));
    }
}
