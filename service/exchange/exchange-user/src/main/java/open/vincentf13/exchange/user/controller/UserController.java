package open.vincentf13.exchange.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import open.vincentf13.common.core.exception.OpenServiceException;
import open.vincentf13.common.spring.mvc.OpenApiResponse;
import open.vincentf13.exchange.user.dto.RegisterUserRequest;
import open.vincentf13.exchange.user.dto.UpdateUserStatusRequest;
import open.vincentf13.exchange.user.dto.UserResponse;
import open.vincentf13.exchange.user.service.UserApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Validated
@RequiredArgsConstructor
public class UserController {

    private final UserApplicationService userService;

    @PostMapping
    public ResponseEntity<OpenApiResponse<UserResponse>> register(@RequestBody @Valid RegisterUserRequest request)
            throws OpenServiceException {
        return ResponseEntity.ok(OpenApiResponse.success(userService.register(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OpenApiResponse<UserResponse>> getById(@PathVariable Long id)
            throws OpenServiceException {
        return ResponseEntity.ok(OpenApiResponse.success(userService.findById(id)));
    }

    @GetMapping
    public ResponseEntity<OpenApiResponse<?>> find(@RequestParam(value = "email", required = false) String email)
            throws OpenServiceException {
        if (email != null && !email.isBlank()) {
            return ResponseEntity.ok(OpenApiResponse.success(userService.findByEmail(email)));
        }
        List<UserResponse> users = userService.listAll();
        return ResponseEntity.ok(OpenApiResponse.success(users));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OpenApiResponse<UserResponse>> updateStatus(@PathVariable Long id,
                                                                       @RequestBody @Valid UpdateUserStatusRequest request)
            throws OpenServiceException {
        return ResponseEntity.ok(OpenApiResponse.success(userService.updateStatus(id, request)));
    }
}
