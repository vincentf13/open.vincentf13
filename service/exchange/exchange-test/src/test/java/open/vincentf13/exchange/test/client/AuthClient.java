package open.vincentf13.exchange.test.client;

import open.vincentf13.exchange.test.AuthLoginApi;
import open.vincentf13.sdk.auth.server.controller.dto.JwtTokenPair;
import open.vincentf13.sdk.auth.server.controller.dto.LoginRequest;

class AuthClient {
    private final AuthLoginApi authApi;

    AuthClient(String gatewayHost) {
        this.authApi = FeignClientSupport.buildClient(AuthLoginApi.class, gatewayHost + "/auth/api");
    }

    String login(String email, String password) {
        JwtTokenPair tokenPair = FeignClientSupport.assertSuccess(
            authApi.login(new LoginRequest(email, password)), "login");
        return tokenPair.jwtToken();
    }
}
