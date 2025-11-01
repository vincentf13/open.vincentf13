package open.vincentf13.exchange.auth.controller;

import lombok.RequiredArgsConstructor;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import open.vincentf13.exchange.auth.sdk.rest.api.AuthCredentialApi;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialCreateRequest;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialPrepareRequest;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialPrepareResponse;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialResponse;
import open.vincentf13.exchange.auth.app.service.AuthCredentialService;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthCredentialController implements AuthCredentialApi {

    private final AuthCredentialService authCredentialService;

    @Override
    public OpenApiResponse<AuthCredentialPrepareResponse> prepare(AuthCredentialPrepareRequest request) {
        return OpenApiResponse.success(authCredentialService.prepare(request));
    }

    @Override
    public OpenApiResponse<AuthCredentialResponse> create(AuthCredentialCreateRequest request) {
        return OpenApiResponse.success(authCredentialService.create(request));
    }
}
