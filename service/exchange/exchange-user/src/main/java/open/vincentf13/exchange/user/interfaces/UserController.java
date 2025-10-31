package open.vincentf13.exchange.user.interfaces;

import lombok.RequiredArgsConstructor;
import open.vincentf13.common.spring.mvc.OpenApiResponse;
import open.vincentf13.exchange.user.api.UserApi;
import open.vincentf13.exchange.user.api.dto.UserRegisterRequest;
import open.vincentf13.exchange.user.api.dto.UserResponse;
import open.vincentf13.exchange.user.api.dto.UserUpdateStatusRequest;
import open.vincentf13.exchange.user.service.UserService;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class UserController implements UserApi {

    private final UserService userService;

    @Override
    public OpenApiResponse<UserResponse> register(UserRegisterRequest request) {
        return OpenApiResponse.success(userService.register(request));
    }

    @Override
    public OpenApiResponse<UserResponse> getById(Long id) {
        return OpenApiResponse.success(userService.findById(id));
    }

    @Override
    public OpenApiResponse<UserResponse> find(String email) {
        return OpenApiResponse.success(userService.findByEmail(email));
    }

    @Override
    public OpenApiResponse<List<UserResponse>> list() {
        return OpenApiResponse.success(userService.listAll());
    }

    @Override
    public OpenApiResponse<UserResponse> updateStatus(Long id, UserUpdateStatusRequest request) {
        return OpenApiResponse.success(userService.updateStatus(id, request));
    }
}
