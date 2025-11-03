package open.vincentf13.exchange.user.controller;

import lombok.RequiredArgsConstructor;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import open.vincentf13.exchange.user.sdk.rest.api.UserApi;
import open.vincentf13.exchange.user.sdk.rest.api.dto.UserRegisterRequest;
import open.vincentf13.exchange.user.sdk.rest.api.dto.UserResponse;
import open.vincentf13.exchange.user.service.UserService;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController implements UserApi {

    private final UserService userService;

    @Override
    public OpenApiResponse<UserResponse> register(UserRegisterRequest request) {
        return OpenApiResponse.success(userService.register(request));
    }

    @Override
    public OpenApiResponse<UserResponse> getMe() {
        return OpenApiResponse.success(userService.getCurrentUser());
    }

    @Override
    public OpenApiResponse<UserResponse> findByEmail(String email) {
        return OpenApiResponse.success(userService.getUserByEmail(email));
    }

}
