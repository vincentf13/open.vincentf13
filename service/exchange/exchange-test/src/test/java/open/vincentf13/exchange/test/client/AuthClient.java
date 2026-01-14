package open.vincentf13.exchange.test.client;

import open.vincentf13.exchange.test.client.api.AuthLoginApi;
import open.vincentf13.exchange.test.client.utils.FeignClientSupport;
import open.vincentf13.sdk.auth.server.controller.dto.JwtTokenPair;
import open.vincentf13.sdk.auth.server.controller.dto.LoginRequest;

public class AuthClient extends BaseClient {
    private final AuthLoginApi authApi;

    public AuthClient(String host) {
        super(host);
        this.authApi = FeignClientSupport.buildClient(AuthLoginApi.class, host + "/auth/api");
    }

    public String login(String email, String password) {
        JwtTokenPair tokenPair = FeignClientSupport.assertSuccess(
            authApi.login(new LoginRequest(email, password)), "login");
        return tokenPair.jwtToken();
    }
}
