package open.vincentf13.exchange.user.interfaces;

import lombok.RequiredArgsConstructor;
import open.vincentf13.common.spring.mvc.OpenApiResponse;
import open.vincentf13.exchange.user.api.UserApi;
import open.vincentf13.exchange.user.api.dto.RegisterUserRequest;
import open.vincentf13.exchange.user.api.dto.UpdateUserStatusRequest;
import open.vincentf13.exchange.user.api.dto.UserResponse;
import open.vincentf13.exchange.user.app.service.UserApplicationService;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController implements UserApi {

    private final UserApplicationService userService;

    @Override
    public OpenApiResponse<UserResponse> register(RegisterUserRequest request) {
        return OpenApiResponse.success(userService.register(request));
    }

    @Override
    public OpenApiResponse<UserResponse> getById(Long id) {
        return OpenApiResponse.success(userService.findById(id));
    }

    @Override
    public OpenApiResponse<?> find(String email) {
        if (email != null && !email.isBlank()) {
            return OpenApiResponse.success(userService.findByEmail(email));
        }
        return OpenApiResponse.success(userService.listAll());
    }

    @Override
    public OpenApiResponse<UserResponse> updateStatus(Long id, UpdateUserStatusRequest request) {
        return OpenApiResponse.success(userService.updateStatus(id, request));
    }
}
