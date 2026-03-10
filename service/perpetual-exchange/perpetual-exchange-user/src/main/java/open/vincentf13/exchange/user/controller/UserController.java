package open.vincentf13.exchange.user.controller;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.user.sdk.rest.api.UserApi;
import open.vincentf13.exchange.user.sdk.rest.api.dto.UserRegisterRequest;
import open.vincentf13.exchange.user.sdk.rest.api.dto.UserResponse;
import open.vincentf13.exchange.user.service.UserCommandService;
import open.vincentf13.exchange.user.service.UserQueryService;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController implements UserApi {

  private final UserCommandService userCommandService;
  private final UserQueryService userQueryService;

  @Override
  public OpenApiResponse<UserResponse> register(UserRegisterRequest request) {
    return OpenApiResponse.success(userCommandService.register(request));
  }

  @Override
  public OpenApiResponse<UserResponse> getMe() {
    return OpenApiResponse.success(userQueryService.getCurrentUser());
  }

  @Override
  public OpenApiResponse<UserResponse> findByEmail(String email) {
    return OpenApiResponse.success(userQueryService.getUserByEmail(email));
  }
}
